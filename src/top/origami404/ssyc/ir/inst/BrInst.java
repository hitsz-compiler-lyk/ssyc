package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.BasicBlock;
import top.origami404.ssyc.ir.IRVerifyException;
import top.origami404.ssyc.ir.type.IRType;

public class BrInst extends Instruction {
    public BrInst(BasicBlock nextBB) {
        super(InstKind.Br, IRType.VoidTy);
        super.addOperandCO(nextBB);

        final var currBlock = this.getParent()
            .orElseThrow(() -> new RuntimeException("Free br instruction"));
        nextBB.addPredecessor(currBlock);
    }

    public BasicBlock getNextBB() {
        return getOperand(0).as(BasicBlock.class);
    }

    @Override
    public void verify() throws IRVerifyException {
        super.verify();

        final var block = getParent().orElseThrow();
        final var nextBB = getNextBB();

        ensure(block != nextBB, "Cannot jump back to itself (maybe?)");
        ensure(nextBB.getPredecessors().contains(block),"NextBB must have currBlock in its predecessors list");
    }
}
