package pass.ir.util;

import ir.*;
import ir.constant.Constant;
import ir.inst.Instruction;
import ir.inst.PhiInst;
import ir.visitor.ValueVisitor;
import utils.Log;

import java.util.*;
import java.util.stream.Collectors;

public class MultiBasicBlockCloner implements ValueVisitor<Value> {
    public MultiBasicBlockCloner(Set<BasicBlock> blocksToBeCloned) {
        this.blocksToBeCloned = blocksToBeCloned;
        this.oldToNew = new HashMap<>();
        this.instructionCloner = new MultiBasicBlockInstructionCloner();
    }

    protected void replaceInstructionCloner(MultiBasicBlockInstructionCloner newInstructionCloner) {
        this.instructionCloner = newInstructionCloner;
    }

    public Set<BasicBlock> convert(Set<BasicBlock> blocks) {
        return new LinkedHashSet<>(convert(new ArrayList<>(blocks)));
    }

    public List<BasicBlock> convert(List<BasicBlock> blocks) {
        final var oldBlocks = blocks.stream().filter(this::shouldBeCloned).collect(Collectors.toList());

        for (final var block : oldBlocks) {
            final var newBlock = getOrCreate(block);
            block.stream().map(this::getOrCreate).forEach(newBlock::add);
            newBlock.adjustPhiEnd();
        }

        // 要保证克隆后各个基本块的前继顺序与克隆之前一样, 防止 phi 的 incoming value 顺序跟 block 的不一样
        for (final var block : oldBlocks) {
            final var newBlock = getOrCreate(block);
            final var newPreds = block.getPredecessors().stream()
                .map(this::getOrCreate).collect(Collectors.toList());

            // 在目标块完全没有前继的情况下, 可以不要求其前继数量等于旧块的前继数量
            // 这意味着在该 cloner 只复制原来函数中的一小块块时, 可以直接忽略开头的那些边界块的前继
            if (newPreds.size() != newBlock.getPredecessorSize()) {
                if (newBlock.getPredecessorSize() == 0) {
                    Log.info("Ignoring the pred of %s (old as %s)".formatted(newBlock, oldBlocks));
                } else {
                    Log.ensure(false);
                }
            } else {
                newBlock.resetPredecessorsOrder(newPreds);
            }
        }

        return blocks.stream().map(this::getOrCreate).collect(Collectors.toList());
    }

    protected BasicBlock createNewBBFromOld(BasicBlock oldBB) {
        return BasicBlock.createFreeBBlock(oldBB.getSymbol().newSymbolWithSuffix("_cloned"));
    }

    protected BasicBlock getOtherBB(BasicBlock blockShouldNotBeCloned) {
        return blockShouldNotBeCloned;
    }

    @Override
    public Value visitBasicBlock(final BasicBlock oldBB) {
        // 外界的 BasicBlock 就不要复制了
        if (shouldNotBeCloned(oldBB)) {
            return getOtherBB(oldBB);
        }

        if (!oldToNew.containsKey(oldBB)) {
            final var newInst = createNewBBFromOld(oldBB);
            oldToNew.put(oldBB, newInst);
            return newInst;
        } else {
            return oldToNew.get(oldBB);
        }
    }


    @Override
    public Value visitInstruction(final Instruction oldInst) {
        // 外界的 Instruction 就不要复制了
        if (shouldNotBeCloned(oldInst)) {
            return oldInst;
        }

        if (!oldToNew.containsKey(oldInst)) {
            final var newInst = instructionCloner.visit(oldInst);
            oldToNew.put(oldInst, newInst);
            return newInst;
        } else {
            return oldToNew.get(oldInst);
        }
    }

    @Override public Value visitFunction(final Function value) { return value; }
    @Override public Value visitGlobalVar(final GlobalVar value) { return value; }
    @Override public Value visitParameter(final Parameter value) { return value; }
    @Override public Value visitConstant(final Constant value) { return value; }

    private boolean shouldBeCloned(BasicBlock block) {
        return blocksToBeCloned.contains(block);
    }

    private boolean shouldNotBeCloned(BasicBlock block) {
        return !shouldBeCloned(block);
    }

    private boolean shouldNotBeCloned(Instruction instruction) {
        return shouldNotBeCloned(instruction.getParent());
    }

    @SuppressWarnings("unchecked")
    protected <T extends Value> T getOrCreate(T old) {
        return (T) visit(old);
    }

    private final Set<BasicBlock> blocksToBeCloned;
    protected final Map<Value, Value> oldToNew;
    protected MultiBasicBlockInstructionCloner instructionCloner;

    protected class MultiBasicBlockInstructionCloner extends SimpleInstructionCloner {
        @Override
        protected <T extends Value> T getNewOperand(final T old) {
            return getOrCreate(old);
        }

        @Override
        public Instruction visitPhiInst(final PhiInst inst) {
            // phi 指令有可能有到自身的引用, 这时候需要先把它构造出来放入, 然后再递归查找
            final var phi = new PhiInst(inst.getType(), inst.getWaitFor());
            oldToNew.put(inst, phi);

            final var incomingValues = inst.getIncomingValues().stream()
                .map(this::getNewOperand).collect(Collectors.toList());
            // 在构建 phi 时, 有可能当前块的前继还没准备好, 所以要调用 unchecked 版本
            phi.setIncomingValueWithoutCheckingPredecessorsCO(incomingValues);

            return phi;
        }
    }
}
