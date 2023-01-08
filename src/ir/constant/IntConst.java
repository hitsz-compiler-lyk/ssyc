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
    public boolean isZero() {
        return value == 0;
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

    @Override
    public boolean equals(final Object obj) {
        return obj instanceof IntConst
            && ((IntConst) obj).value == value;
    }

    @Override
    public int hashCode() {
        return value;
    }

    private final int value;
}
