package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.BasicBlock;
import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.type.IRType;

public class BrCondInst extends Instruction {
    public BrCondInst(Value cond, BasicBlock trueBB, BasicBlock falseBB) {
        super(InstKind.BrCond, IRType.VoidTy);

        super.addOperandCO(cond);
        super.addOperandCO(trueBB);
        super.addOperandCO(falseBB);

        final var currBlock = this.getParent()
            .orElseThrow(() -> new RuntimeException("Free BrCond instruction"));
        trueBB.addPredecessor(currBlock);
        falseBB.addPredecessor(currBlock);

        assert cond.getType().getKind().isBool()
            : "BrCond expect a cond with Bool IRType";
    }

    public Value getCond() { return getOperand(0); }
    public BasicBlock getTrueBB() {
        return getOperand(1).as(BasicBlock.class);
    }
    public BasicBlock getFalseBB() {
        return getOperand(2).as(BasicBlock.class);
    }
}
