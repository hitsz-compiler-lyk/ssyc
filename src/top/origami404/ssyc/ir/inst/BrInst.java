package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.BasicBlock;
import top.origami404.ssyc.ir.type.IRType;

public class BrInst extends Instruction {
    public BrInst(BasicBlock nextBB) {
        // TODO: 维护基本块前后继关系
        super(InstKind.Br, IRType.BBlockTy);
        this.nextBB = nextBB;
        super.addOperandCO(nextBB);

        nextBB.addPredecessor(this.getParent().get());
    }

    public BasicBlock getNextBB() {
        return nextBB;
    }

    private BasicBlock nextBB;
}
