package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.arg.Value;

public class CompareInst extends Inst {
    public CompareInst(Value left, Value right) {
        super(Kind.CMP, null, left, right);
    }

    public Value getLeft()  { return castTo(arg1, Value.class); }
    public Value getRight() { return castTo(arg2, Value.class); }
}