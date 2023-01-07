package ir.constant;

import ir.IRVerifyException;
import ir.type.IRType;

public class FloatConst extends Constant {
    FloatConst(float value) {
        super(IRType.FloatTy);
        this.value = value;
    }

    public float getValue() {
        return value;
    }

    @Override
    public boolean isZero() {
        return value == 0.0f;
    }

    @Override
    public void verify() throws IRVerifyException {
        super.verify();
        ensureNot(value == 0.0f && this != Constant.FLOAT_0,
                "Float 0 should be unique");
    }

    @Override
    public String toString() {
        // Float.toHexString 只会返回 IEEE 标准的十六进制浮点数格式
        // 而这个方法返回的是二进制位相同的 float 的十六进制整数表示
        final var lifted = (double) value;
        final var bits = Double.doubleToLongBits(lifted);
        final var hex = Long.toHexString(bits).toUpperCase();
        return "0x" + hex;
    }

    @Override
    public boolean equals(final Object obj) {
        return obj instanceof FloatConst
               && ((FloatConst) obj).value == value;
    }

    @Override
    public int hashCode() {
        return Float.floatToIntBits(value);
    }

    private final float value;
}
