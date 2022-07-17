package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.BasicBlock;
import top.origami404.ssyc.ir.IRVerifyException;
import top.origami404.ssyc.ir.type.IRType;
import top.origami404.ssyc.utils.Log;

public class BrInst extends Instruction {
    public BrInst(BasicBlock nextBB, BasicBlock currBlock) {
        super(InstKind.Br, IRType.VoidTy);
        super.addOperandCO(nextBB);

        insertPredecessor(nextBB, currBlock);
    }

    private static void insertPredecessor(final BasicBlock toBB, final BasicBlock currBlock) {
        if (!toBB.getPredecessors().contains(currBlock)) {
            toBB.addPredecessor(currBlock);
        } else {
            Log.info("CurrBB %s already in %s".formatted(currBlock, toBB));
        }
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
