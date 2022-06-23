package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.BasicBlock;
import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.type.IRType;

public class BrCondInst extends Instruction {
    BrCondInst(Value cond, BasicBlock trueBB, BasicBlock falseBB) {
        super(InstKind.BrCond, IRType.VoidTy);

        this.cond = cond;
        this.trueBB = trueBB;
        this.falseBB = falseBB;

        super.addOperandCO(cond);
        super.addOperandCO(trueBB);
        super.addOperandCO(falseBB);

        assert cond.getType().getKind().isBool()
            : "BrCond expect a cond with Bool IRType";
    }

    public Value getCond() {
        return cond;
    }

    public BasicBlock getTrueBB() {
        return trueBB;
    }

    public BasicBlock getFalseBB() {
        return falseBB;
    }

    private Value cond;
    private BasicBlock trueBB;
    private BasicBlock falseBB;
}
