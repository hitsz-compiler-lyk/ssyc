package top.origami404.ssyc.ir.constant;

import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.type.IRType;

public class Constant extends Value {
    protected Constant(IRType type) {
        super(type);
        assert type.getKind().isInt() || type.getKind().isFloat()
            : "Only support INT or FLOAT constants";
    }

    public static IntConst createIntConstant(int value) { return new IntConst(value); }
    public static FloatConst createFloatConstant(float value) { return new FloatConst(value); }
}
