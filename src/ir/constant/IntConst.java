package ir.constant;

import ir.IRVerifyException;
import ir.type.IRType;

public class IntConst extends Constant {
    IntConst(int value) {
        super(IRType.IntTy);
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

    @Override
    public String toString() {
        return Integer.toString(value);
    }

    private final int value;
}