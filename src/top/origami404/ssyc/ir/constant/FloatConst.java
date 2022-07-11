package top.origami404.ssyc.ir.constant;

import top.origami404.ssyc.ir.IRVerifyException;
import top.origami404.ssyc.ir.type.IRType;

public class FloatConst extends Constant {
    FloatConst(float value) {
        super(IRType.FloatTy);
        super.setName(Float.toHexString(value));
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

    private final float value;
}
