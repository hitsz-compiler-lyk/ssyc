package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.arg.BBlock;

public class BranchInst extends Inst {
    /**
     * 无条件跳转
     */
    public BranchInst(BBlock to) {
        super(Kind.Br, null, to, null);
    }

    /**
     * 普通跳转
     * @param brKind 跳转类型 (Beq, Bne, ...)
     */
    public BranchInst(Kind brKind, BBlock trueBlock, BBlock falseBlock) {
        super(brKind, null, trueBlock, falseBlock);
    }

    public BBlock getTrueBlock()  { return castTo(arg1, BBlock.class); }
    public BBlock getFalseBlock() { return castTo(arg2, BBlock.class); }
    public BBlock getToBlock()    { return castTo(arg1, BBlock.class); }
}