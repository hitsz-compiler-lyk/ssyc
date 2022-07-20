package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.BasicBlock;
import top.origami404.ssyc.ir.IRVerifyException;
import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.constant.Constant;
import top.origami404.ssyc.ir.type.IRType;
import top.origami404.ssyc.utils.Log;

public class BrCondInst extends Instruction {
    public BrCondInst(BasicBlock currBlock, Value cond, BasicBlock trueBB, BasicBlock falseBB) {
        super(InstKind.BrCond, IRType.VoidTy);

        super.addOperandCO(cond);
        super.addOperandCO(trueBB);
        super.addOperandCO(falseBB);

        insertPredecessor(trueBB, currBlock);
        insertPredecessor(falseBB, currBlock);

        assert cond.getType().getKind().isBool()
            : "BrCond expect a cond with Bool IRType";
    }

    private static void insertPredecessor(final BasicBlock toBB, final BasicBlock currBlock) {
        if (!toBB.getPredecessors().contains(currBlock)) {
            toBB.addPredecessor(currBlock);
        } else {
            Log.info("CurrBB %s already in %s".formatted(currBlock, toBB));
        }
    }

    public Value getCond() { return getOperand(0); }
    public BasicBlock getTrueBB() {
        return getOperand(1).as(BasicBlock.class);
    }
    public BasicBlock getFalseBB() {
        return getOperand(2).as(BasicBlock.class);
    }

    @Override
    public void verify() throws IRVerifyException {
        super.verify();

        final var block = getParentOpt().orElseThrow();
        final var trueBB = getTrueBB();
        final var falseBB = getFalseBB();

        ensure(block != trueBB && block != falseBB, "Cannot jump back to itself (maybe?)");
        ensure(trueBB != falseBB, "TrueBB shouldn't be as same as falseBB");

        ensureNot(getCond() instanceof Constant, "Cond shouldn't be constant");

        ensure(trueBB.getPredecessors().contains(block), "TrueBB must have currBlock in its predecessors list");
        ensure(falseBB.getPredecessors().contains(block), "FalseBB must have currBlock in its predecessors list");
    }
}
