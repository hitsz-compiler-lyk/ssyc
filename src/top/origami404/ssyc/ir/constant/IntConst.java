package top.origami404.ssyc.ir.constant;

import top.origami404.ssyc.ir.type.IRType;

public class IntConst extends Constant {
    IntConst(int value) {
        super(IRType.IntTy);
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    private int value;
}