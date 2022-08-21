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
    public void verify() throws IRVerifyException {
        super.verify();
        ensureNot(value == 0.0f && this != Constant.FLOAT_0,
                "Float 0 should be unique");
    }

    @Override
    public String toString() {
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
