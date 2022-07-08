package top.origami404.ssyc.ir.constant;

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

    private final float value;
}
