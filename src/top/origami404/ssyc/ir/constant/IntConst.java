package top.origami404.ssyc.ir.constant;

import top.origami404.ssyc.ir.IRVerifyException;
import top.origami404.ssyc.ir.type.IRType;

public class IntConst extends Constant {
    IntConst(int value) {
        super(IRType.IntTy);
        super.setName(Integer.toString(value));
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    @Override
    public void verify() throws IRVerifyException {
        super.verify();
        ensureNot(value == 0 && this != Constant.INT_0,
                "Int 0 should be unique");
    }

    private final int value;
}
