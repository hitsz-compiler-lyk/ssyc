package pass.ir.loop;

import ir.*;
import ir.Module;
import ir.constant.Constant;
import ir.inst.*;
import ir.visitor.ValueVisitor;
import pass.ir.IRPass;
import pass.ir.util.SimpleInstructionCloner;
import utils.Log;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class MoveLoopInvariant implements IRPass {
    @Override
    public void runPass(final Module module) {

    }

    public void runOnFunction(Function function) {
        final var naturalLoopCollector = new CollectNatrualLoop();
        naturalLoopCollector.runOnFunction(function);

        final var loops = function.getAnalysisInfo(LoopFunctionInfo.class).getAllLoopsInPostOrder();


    }

    List<Instruction> collectInvariant(NaturalLoop loop) {
        loop.getHeader().phis().forEach(this::collectVariants);

        return loop.getBlocks().stream()
            .flatMap(List<Instruction>::stream)
            .filter(this::isInvariant)
            .collect(Collectors.toUnmodifiableList());
    }

    void moveInvariant(NaturalLoop loop, List<Instruction> invariants) {
        // 大体上架构变换如下图: (# 线表示可能有 0 到多个跳转)
        /*
         *                                                  #
         *                                                  |
         *                                        +---------v-----------+
         *             #                          | HeadCond            | F
         *             #                          | (Phi from outside)  +----------+
         *             #                          +---------+-----------+          |
         *             |                                    | T                    |
         *     +-------v--------+                 +---------v-----------+          |
         *     |                |                 | Pre-header          |          |
         * ###->  Cond (header) | F               |(Place for invariant)|          |
         * #   |                +-+               +---------+-----------+          |
         * #   +-------+--------+ |                         |                      |
         * #           | T        |               +---------v-----------+          |
         * #   +-------v--------+ |    ======>    | Pre-body            |          |
         * #   |                | |    ======>    | (Phi from head/tail)<----+     |
         * #####  Body...       | |    ======>    +---------+-----------+    |     |
         *     |                | |                         |                |     |
         *     +-------#--------+ |               ----------v-----------+    |     |
         *             #          |            #### Body...             |    |     |
         *             #          |            #  |                     |    |     |
         *             |          |            #  +---------#-----------+    |     |
         *     +-------v--------+ |            #            #                |     |
         *     |                | |            #  +---------v-----------+    |     |
         *     | Exit (Outside) <-+            #  | TailCond            | T  |     |
         *     |                |              #  | (Phi from bodies)   +----+     |
         *     +----------------+              #  +---------+-----------+          |
         *                                     #            | F                    |
         *                                     #  +---------v-----------+          |
         *                                     #  | TailExit            <----------+
         *                                     #  | (Phi from head/tail)|
         *                                     #  +---------+-----------+
         *                                     #            |
         *                                     #            |
         *                                     #            |
         *                                     #  +---------v----------+
         *                                     #  | Exit               |
         *                                     ##-> (Outside)          |
         *                                        +--------------------+
         */

        final var header = loop.getHeader();
        final var originalSymbol = header.getSymbol();

        final var headCond  = BasicBlock.createFreeBBlock(originalSymbol.newSymbolWithName("head_cond"));
        final var preHeader = BasicBlock.createFreeBBlock(originalSymbol.newSymbolWithName("pre_header"));
        final var preBody   = BasicBlock.createFreeBBlock(originalSymbol.newSymbolWithName("pre_body"));
        final var tailCond  = BasicBlock.createFreeBBlock(originalSymbol.newSymbolWithName("tail_cond"));
        final var tailExit  = BasicBlock.createFreeBBlock(originalSymbol.newSymbolWithName("tail_exit"));

        Log.ensure(header.getTerminator() instanceof BrCondInst);
        final var bodyEntry = ((BrCondInst) header.getTerminator()).getTrueBB();

        // =========================== headCond ===========================
        final var blockOutsideIndices = findForIndices(header.getPredecessors(), block -> !loop.containsBlock(block));
        makeNewCond(loop, headCond, blockOutsideIndices, preHeader, tailExit);

        // =========================== pre-header ===========================
        // pre-header 里放循环不变量
        preHeader.addAll(invariants);
        preHeader.add(new BrInst(preHeader, preBody));

        // =========================== tail-cond ===========================
        final var blockInsideIndices = findForIndices(header.getPredecessors(), loop::containsBlock);
        makeNewCond(loop, tailCond, blockInsideIndices, preBody, tailExit);

        // =========================== pre-body ===========================
        // pre-body 里放对两个 Cond 的变量的合并的 phi
        final var phiCount = header.phis().size();
        Log.ensure(phiCount == headCond.phis().size() && phiCount == tailCond.phis().size());

        final var phiToReplaceInBody = new HashMap<Value, PhiInst>();
        for (int i = 0; i < phiCount; i++) {
            // TODO: 也许可以将各个对 phis() 的调用放到循环外以提升性能
            final var oldPhi = header.phis().get(i);
            final var phiFromHead = headCond.phis().get(i);
            final var phiFromTail = tailCond.phis().get(i);

            final var newPhi = emptyPhiFrom(oldPhi);
            // 注意这里的顺序: 我们先构建了 pre-header 再构建 tail-cond 的, 所以才是这个顺序
            // 如果后面顺序改了那么就需要改这个的顺序了
            newPhi.setIncomingCO(List.of(phiFromHead, phiFromTail));

            preBody.add(newPhi);
            phiToReplaceInBody.put(oldPhi, newPhi);
        }

        preBody.add(new BrInst(preBody, bodyEntry));

        // ========================= body ===============================
        // 将 body 里面所有对旧 phi 的使用变为对新 phi 的使用
        for (final var block : loop.getBodyBlocks()) {
            for (final var inst : block) {
                final var oldOperands = IRPass.copyForChange(inst.getOperands());
                for (final var op : oldOperands) {
                    final var replacement = phiToReplaceInBody.get(op);
                    if (replacement != null) {
                        inst.replaceOperandCO(op, replacement);
                    }
                }
            }
        }

        // ========================= tail-exit ===============================
        // 合并从 pre-body 与 head-cond 传入的两个 phi
        // 同时将外界对原来 header 的所有 phi 的使用替换成这个块中的 phi
        for (int i = 0; i < phiCount; i++) {
            final var oldPhi = header.phis().get(i);
            final var phiFromPreBody    = preBody.phis().get(i);
            final var phiFromHeadCond   = headCond.phis().get(i);

            final var newPhi = emptyPhiFrom(oldPhi);
            // 这里同样要注意顺序
            newPhi.setIncomingValueWithoutCheckingPredecessorsCO(List.of(phiFromHeadCond, phiFromPreBody));

            tailExit.add(newPhi);
            oldPhi.replaceAllUseWith(newPhi);
        }

        // 清理 header
        IRPass.copyForChange(header.getPredecessors()).forEach(header::removePredecessorWithPhiUpdated);
        header.freeAllWithoutCheck();

        // 维护 loop
        loop.setHeader(preBody);
        loop.addBlockCO(tailCond);
    }

    PhiInst emptyPhiFrom(PhiInst oldPhi) {
        return new PhiInst(oldPhi.getType(), oldPhi.getWaitFor());
    }

    static void makeNewCond(
        NaturalLoop loop, BasicBlock newCond, List<Integer> inheritIndices,
        BasicBlock newTrueBB, BasicBlock newFalseBB
    ) {
        final var header = loop.getHeader();
        // newCond 里面要根据旧 header 里的 phi 构造新的, 只收从特定前继传过来的参数的 phi
        final var phiReplacement = new HashMap<PhiInst, PhiInst>();
        for (final var phi : header.phis()) {
            final var newPhi = new PhiInst(phi.getType(), phi.getWaitFor());

            final var incomingValueOutsideLoop = selectFrom(phi.getIncomingValues(), inheritIndices);
            newPhi.setIncomingValueWithoutCheckingPredecessorsCO(incomingValueOutsideLoop);

            newCond.addPhi(newPhi);
            phiReplacement.put(phi, newPhi);
        }
        newCond.adjustPhiEnd();

        // 然后要将 cond 中所有剩余部分传进来替换掉
        final var headCondCloner = new ReplaceCloner(loop, phiReplacement, newCond, newTrueBB, newFalseBB);
        header.nonPhis().stream().map(headCondCloner::get).forEach(newCond::add);

        // 然后更新外面前继的指向
        final var outsidePreds = selectFrom(header.getPredecessors(), inheritIndices);
        for (final var outsidePred : outsidePreds) {
            outsidePred.getTerminator().replaceOperandCO(header, newCond);
            newCond.addPredecessor(outsidePred);
        }
    }

    static <T> List<T> selectFrom(List<T> list, List<Integer> indices) {
        final var result = new ArrayList<T>();
        indices.forEach(i -> result.add(list.get(i)));
        return result;
    }

    static <T> List<Integer> findForIndices(List<T> list, Predicate<T> predicate) {
        final var result = new ArrayList<Integer>();

        final var iter = list.iterator();
        for (int i = 0; iter.hasNext(); i++) {
            final var elm = iter.next();
            if (predicate.test(elm)) {
                result.add(i);
            }
        }

        return result;
    }

    boolean isInvariant(Instruction instruction) {
        final var isVariant = variants.contains(instruction)
            || instruction instanceof MemInitInst
            || instruction instanceof CallInst
            || instruction instanceof BrInst;
        // 不使用 variant 的 Store 和 Load 是 invariant 的

        return !isVariant;
    }

    void collectVariants(Instruction instruction) {
        instruction.getUserList().stream()
            .filter(Instruction.class::isInstance).map(Instruction.class::cast)
            .forEach(this::collectVariants);

        variants.add(instruction);
    }

    private final Set<Instruction> variants = new LinkedHashSet<>();
}

class ReplaceCloner implements ValueVisitor<Value> {
    public ReplaceCloner(
        NaturalLoop loop, Map<PhiInst, PhiInst> phiReplacements,
        BasicBlock newBB, BasicBlock newTrueBB, BasicBlock newFalseBB
    ) {
        this.loop = loop;
        this.replacement = new HashMap<>(phiReplacements);

        this.newBB = newBB;
        this.newTrueBB = newTrueBB;
        this.newFalseBB = newFalseBB;
    }

    public Instruction get(Instruction oldInst) {
        return instructionCloner.visit(oldInst);
    }

    @Override
    public Value visitInstruction(final Instruction inst) {
        final var block = inst.getParent();
        if (!loop.containsBlock(block)) {
            return inst;
        }
        Log.ensure(block == loop.getHeader(), "Header should only use value in header");

        if (!replacement.containsKey(inst)) {
            final var newInst = instructionCloner.visit(inst);
            replacement.put(inst, newInst);
        }

        return replacement.get(inst);
    }

    @Override public Value visitBasicBlock(final BasicBlock value)  {
        Log.ensure(false, "Should NOT clone a basic block in loop invariant move");
        throw new RuntimeException();
    }

    @Override public Value visitConstant(final Constant value)      { return value; }
    @Override public Value visitFunction(final Function value)      { return value; }
    @Override public Value visitGlobalVar(final GlobalVar value)    { return value; }
    @Override public Value visitParameter(final Parameter value)    { return value; }

    private final NaturalLoop loop;
    private final BasicBlock newBB;
    private final BasicBlock newTrueBB;
    private final BasicBlock newFalseBB;
    private final Map<Instruction, Instruction> replacement;
    private final InstructionReplaceCloner instructionCloner = new InstructionReplaceCloner();

    class InstructionReplaceCloner extends SimpleInstructionCloner {
        @SuppressWarnings("unchecked")
        @Override protected <T extends Value> T getNewOperand(final T value) {
            return (T) ReplaceCloner.this.visit(value);
        }

        @Override
        public Instruction visitBrCondInst(final BrCondInst inst) {
            final var cond = getNewOperand(inst.getCond());
            return new BrCondInst(newBB, cond, newTrueBB, newFalseBB);
        }

        @Override
        public Instruction visitBrInst(final BrInst inst) {
            Log.ensure(false, "Should NOT clone Br in loop invariant move");
            throw new RuntimeException();
        }

        @Override
        public Instruction visitPhiInst(final PhiInst inst) {
            Log.ensure(false, "Should NOT clone Phi in loop invariant move");
            throw new RuntimeException();
        }

        @Override
        public Instruction visitReturnInst(final ReturnInst inst) {
            Log.ensure(false, "Should NOT clone Return in loop invariant move");
            throw new RuntimeException();
        }
    }
}
