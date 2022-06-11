package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.BasicBlock;
import top.origami404.ssyc.ir.type.IRType;

public class BrInst extends Instruction {
    BrInst(BasicBlock nextBB) {
        super(InstKind.Br, IRType.BBlockTy);
        this.nextBB = nextBB;
        super.addOperand(nextBB);
    }

    public BasicBlock getNextBB() {
        return nextBB;
    }

    private BasicBlock nextBB;
}
