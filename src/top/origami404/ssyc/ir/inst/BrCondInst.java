package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.BasicBlock;
import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.type.IRType;

public class BrCondInst extends Instruction {
    public BrCondInst(Value cond, BasicBlock trueBB, BasicBlock falseBB) {
        super(InstKind.BrCond, IRType.VoidTy);

        // TODO: 维护基本块前后继关系
        this.cond = cond;
        this.trueBB = trueBB;
        this.falseBB = falseBB;

        super.addOperandCO(cond);
        super.addOperandCO(trueBB);
        super.addOperandCO(falseBB);

        trueBB.addPredecessor(this.getParent().get());
        falseBB.addPredecessor(this.getParent().get());

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
