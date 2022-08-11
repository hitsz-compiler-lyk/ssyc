package pass.ir.loop;

import ir.*;
import ir.constant.Constant;
import ir.inst.*;
import ir.visitor.ValueVisitor;
import pass.ir.util.SimpleInstructionCloner;
import utils.Log;

import java.util.*;
import java.util.stream.Collectors;

public class LoopUnroll implements LoopPass {
    @Override
    public void runOnLoop(final CanonicalLoop loop) {
        final var forLoop = ForLoop.tryConvertFrom(loop);

        if (forLoop == null) {
            return;
        }

        final var symbol = forLoop.getLoop().getHeader().getSymbol();

        final var unrolledLoopHeader = BasicBlock.createFreeBBlock(symbol.newSymbolWithSuffix("_unroll"));


    }

    // void runOnFunction(Function function) {
    //     final var collector = new CollectLoopsAndMakeItCanonical();
    //     final var canonicalLoops = collector.collect(function);
    //     final var loops = CanonicalLoop.getAllLoopInPostOrder(canonicalLoops).stream()
    //         .map(ForLoop::tryConvertFrom).collect(Collectors.toList());
    //
    //     Log.debug("Find for-loop: #" + loops.size());
    // }
}

class ForLoop {
    public static ForLoop tryConvertFrom(CanonicalLoop loop) {
        return tryConvertFromOpt(loop).orElse(null);
    }

    public static Optional<ForLoop> tryConvertFromOpt(CanonicalLoop loop) {
        if (loop.isRotated() || !loop.hasUniqueExit()) {
            return Optional.empty();
        }

        final var forLoop = new ForLoop(loop);

        if (forLoop.hasForCond() && forLoop.hasSmallBody() && forLoop.hasForLatch() && forLoop.hasSimpleExit()) {
            return Optional.of(forLoop);
        } else {
            return Optional.empty();
        }
    }

    public CanonicalLoop getLoop() {
        return loop;
    }

    public LoopInvariantInfo getInfo() {
        return info;
    }

    public PhiInst getIndexPhi() {
        return indexPhi;
    }

    public Value getBegin() {
        return begin;
    }

    public Value getEnd() {
        return end;
    }

    public Value getStep() {
        return step;
    }

    boolean hasForCond() {
        // header 的条件必须是 BrCond
        final var terminator = loop.getHeader().getTerminator();
        if (terminator instanceof BrCondInst) {

            // 并且这个 Cond 必须得是比较
            final var cond = ((BrCondInst) terminator).getCond();
            if (cond instanceof CmpInst) {

                // 这个比较一定得是 <
                // TODO: 扩展到 <=
                final var cmp = ((CmpInst) cond);
                final var kind = cmp.getKind();
                if (kind == InstKind.ICmpLt) {

                    // 然后比较对象必须得是一个 phi, 一个循环无关量
                    final var lhs = cmp.getLHS();
                    final var rhs = cmp.getRHS();
                    if (lhs instanceof PhiInst && info.isInvariant(rhs)) {
                        this.indexPhi = ((PhiInst) lhs);
                        this.end = rhs;
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private final static int MAX_BLOCK_ALLOWED_IN_BODY = 5;
    boolean hasSmallBody() {
        return loop.getBody().size() <= MAX_BLOCK_ALLOWED_IN_BODY;
    }

    boolean hasForLatch() {
        final var latch = loop.getLatch();

        // latch 必须是一个直接跳转
        if (latch.getTerminator() instanceof BrInst) {
            Log.ensure(((BrInst) latch.getTerminator()).getNextBB() == loop.getHeader());

            for (final var inst : latch) {
                // latch 里面必须存在对索引变量的更新指令
                if (inst.getKind() == InstKind.IAdd) {
                    final var binop = (BinaryOpInst) inst;
                    final var lhs = binop.getLHS();
                    final var rhs = binop.getRHS();

                    // 形如 x = phi + step 的形式
                    if (lhs == indexPhi && info.isInvariant(rhs)) {
                        final var indexPhiOps = indexPhi.getOperands();
                        Log.ensure(indexPhiOps.size() == 2);
                        this.step = rhs;

                        // 并且 x 要作为 phi 的成员之一
                        if (indexPhiOps.contains(binop)) {
                            final var other = getOtherOne(indexPhiOps, binop);
                            // 并且 phi 的另一个成员必须是从循环外界继承而来的循环无关变量
                            // 似乎按正规循环的要求, header 必须要有 pre-header, 所以另一个似乎必然是循环无关变量?
                            Log.ensure(info.isInvariant(other));
                            // return info.isInvariant(other);
                            this.begin = other;
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    boolean hasSimpleExit() {
        final var exit = loop.getUniqueExit();
        return exit.getPredecessorSize() == 1;
    }

    private <T> T getOtherOne(Collection<T> collection, T one) {
        Log.ensure(collection.size() == 2);
        final var set = new HashSet<>(collection);
        Log.ensure(set.size() == 2);

        set.remove(one);
        return set.iterator().next();
    }

    private ForLoop(CanonicalLoop loop) {
        this.loop = loop;
        this.info = LoopInvariantInfo.collect(loop);
    }

    private CanonicalLoop loop;
    private LoopInvariantInfo info;
    private PhiInst indexPhi;
    private Value begin;
    private Value end;
    private Value step;
    // private InstKind condKind;
}

class ForBodyCloner implements ValueVisitor<Value> {
    public ForBodyCloner(CanonicalLoop loop, int unrollCount) {
        this.loop = loop;
        this.unrollCount = unrollCount;

        this.oldToNew = new HashMap<>();
        this.instructionCloner = new InstructionCloner();
    }

    @Override
    public Value visitBasicBlock(final BasicBlock oldBB) {
        // 外界的 BasicBlock 就不要复制了
        if (loop.isNotInBody(oldBB)) {
            return oldBB;
        }

        oldToNew.computeIfAbsent(oldBB,
            key -> BasicBlock.createFreeBBlock(key.getSymbol().newSymbolWithSuffix("_unroll_" + unrollCount)));
        return oldToNew.get(oldBB);
    }


    @Override
    public Value visitInstruction(final Instruction oldInst) {
        // 外界的 Instruction 就不要复制了
        if (loop.isNotInBody(oldInst.getParent())) {
            return oldInst;
        }

        oldToNew.computeIfAbsent(oldInst, key -> instructionCloner.visit((Instruction) key));
        return oldToNew.get(oldInst);
    }

    @Override public Value visitFunction(final Function value) { return value; }
    @Override public Value visitGlobalVar(final GlobalVar value) { return value; }
    @Override public Value visitParameter(final Parameter value) { return value; }
    @Override public Value visitConstant(final Constant value) { return value; }

    private final int unrollCount;
    private final Map<Value, Value> oldToNew;
    private final CanonicalLoop loop;
    private final InstructionCloner instructionCloner;

    class InstructionCloner extends SimpleInstructionCloner {
        @Override
        @SuppressWarnings("unchecked")
        protected <T extends Value> T getNewOperand(final T old) {
            return (T) ForBodyCloner.this.visit(old);
        }

        @Override
        public Instruction visitPhiInst(final PhiInst inst) {
            final var phi = new PhiInst(inst.getType(), inst.getWaitFor());
            oldToNew.put(inst, phi);

            final var incomingValues = inst.getIncomingValues().stream()
                .map(this::getNewOperand).collect(Collectors.toList());
            phi.setIncomingValueWithoutCheckingPredecessorsCO(incomingValues);

            return phi;
        }
    }
}