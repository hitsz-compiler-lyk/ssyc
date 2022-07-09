package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.BasicBlock;
import top.origami404.ssyc.ir.type.IRType;

public class BrInst extends Instruction {
    public BrInst(BasicBlock nextBB) {
        // TODO: 维护基本块前后继关系
        super(InstKind.Br, IRType.VoidTy);
        super.addOperandCO(nextBB);

        final var currBlock = this.getParent()
            .orElseThrow(() -> new RuntimeException("Free br instruction"));
        nextBB.addPredecessor(currBlock);
    }

    public BasicBlock getNextBB() {
        return getOperand(0).as(BasicBlock.class);
    }
}
