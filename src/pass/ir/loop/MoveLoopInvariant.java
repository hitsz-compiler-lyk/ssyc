package pass.ir.loop;

import ir.*;
import ir.Module;
import ir.constant.Constant;
import ir.inst.*;
import ir.visitor.InstructionVisitor;
import ir.visitor.ValueVisitor;
import pass.ir.IRPass;
import pass.ir.util.SimpleInstructionCloner;
import utils.Log;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class MoveLoopInvariant implements IRPass {
    @Override
    public void runPass(final Module module) {

    }



    List<Instruction> collectInvariant(NaturalLoop loop) {
        loop.getHeader().phis().forEach(this::collectVariants);

        return loop.getBlocks().stream()
            .flatMap(List<Instruction>::stream)
            .filter(this::isInvariant)
            .collect(Collectors.toUnmodifiableList());
    }

    void moveInvariant(NaturalLoop loop, List<Instruction> invariants) {
        // 大体上架构变换如下图:
        /*
         *                                           │      │        │
         *                                        ┌──▼──────▼────────▼──┐
         *                                        │ HeadCond (header)   │ F
         *                                        │ (Phi for outside)   ├──────────┐
         *                                        └─────────┬───────────┘          │
         *                                                  │ T                    │
         *        │    │   │                      ┌─────────▼───────────┐          │
         *        │    │   │                      │ Pre-header          │          │
         *        │    │   │                      │(Place for invariant)│          │
         *     ┌──▼────▼───▼────┐                 └─────────┬───────────┘          │
         *     │                │                           │                      │
         * ┌───►  Cond (header) │ F               ┌─────────▼───────────┐          │
         * │   │                ├─┐    ======>    │ Pre-body            │          │
         * │   └───────┬────────┘ │    ======>    │ (Phi for body)      ◄────┐     │
         * │           │ T        │    ======>    └─────────┬───────────┘    │     │
         * │   ┌───────▼────────┐ │                         │                │     │
         * │   │                │ │               ┌─────────▼───────────┐    │     │
         * └───┤  Body...       │ │               │ Body...             │    │     │
         *     │                │ │               │                     │    │     │
         *     └────────────────┘ │               └─────────┬───────────┘    │     │
         *                        │                         │                │     │
         *                        │               ┌─────────▼───────────┐    │     │
         *                        ▼               │ TailCond            │ T  │     │
         *                                        │ (Copy for cond)     ├────┘     │
         *                                        └─────────┬───────────┘          │
         *                                                  │ F                    │
         *                                        ┌─────────▼──────────┐           │
         *                                        │ TailExit           │           │
         *                                        │ (unique exit point)◄───────────┘
         *                                        └─────────┬──────────┘
         *                                                  │
         *                                                  ▼
         */
        // 要注意的细节:
        //      1. 原 Cond 里的 phi 要 "分裂":
        //          来自外界的值要在 cond 里合一个 phi, 然后来自 body 的值要再在 pre-body 里合一次 phi

        final var header = loop.getHeader();
        final var originalSymbol = header.getSymbol();

        final var headCond  = BasicBlock.createFreeBBlock(originalSymbol.newSymbolWithName("head_cond"));
        final var preHeader = BasicBlock.createFreeBBlock(originalSymbol.newSymbolWithName("pre_header"));
        final var preBody   = BasicBlock.createFreeBBlock(originalSymbol.newSymbolWithName("pre_body"));
        final var tailCond  = BasicBlock.createFreeBBlock(originalSymbol.newSymbolWithName("tail_cond"));
        final var tailExit  = BasicBlock.createFreeBBlock(originalSymbol.newSymbolWithName("tail_exit"));

        // =========================== headCond ===========================
        // headCond 里面要根据旧 header 里的 phi 构造新的, 只收从外界传进来的参数的 phi
        final var phiReplacement = new HashMap<PhiInst, PhiInst>();
        final var blockOutsideIndices = findForIndices(header.getPredecessors(), block -> !loop.contianBlocks(block));
        for (final var phi : header.phis()) {
            final var newPhi = new PhiInst(phi.getType(), phi.getWaitFor());

            final var incomingValueOutsideLoop = selectFrom(phi.getIncomingValues(), blockOutsideIndices);
            newPhi.setIncomingValueWithoutCheckingPredecessorsCO(incomingValueOutsideLoop);

            headCond.addPhi(newPhi);
            phiReplacement.put(phi, newPhi);
        }
        headCond.adjustPhiEnd();

        // 然后要将 cond 中所有剩余部分传进来替换掉
        final var headCondCloner = new ReplaceCloner(loop, phiReplacement, headCond, preHeader, tailExit);
        header.nonPhis().stream().map(headCondCloner::get).forEach(headCond::add);

        // 然后更新外面前继的指向
        final var outsidePreds = selectFrom(header.getPredecessors(), blockOutsideIndices);
        for (final var outsidePred : outsidePreds) {
            outsidePred.getTerminator().replaceOperandCO(header, headCond);
            headCond.addPredecessor(outsidePred);
        }

        // 然后删除旧 header 的所有外面的前继
        outsidePreds.forEach(header::removePredecessorWithPhiUpdated);


        // =========================== pre-header ===========================
        // pre-header 里放循环不变量
        preHeader.addAll(invariants);
        preHeader.add(new BrInst(preHeader, preBody));


        // =========================== pre-body ===========================
        // pre-body 里要根据旧 header 的 phi 构造新的, 只收从循环内部传进来的参数的 phi


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

    private Set<Instruction> variants = new LinkedHashSet<>();
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
        if (!loop.contianBlocks(block)) {
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
