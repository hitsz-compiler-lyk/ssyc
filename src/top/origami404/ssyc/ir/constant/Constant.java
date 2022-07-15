package top.origami404.ssyc.ir.constant;

import java.util.List;

import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.constant.ArrayConst.ZeroArrayConst;
import top.origami404.ssyc.ir.type.ArrayIRTy;
import top.origami404.ssyc.ir.type.IRType;

public class Constant extends Value {
    protected Constant(IRType type) {
        super(type);
        assert type.getKind().isInt() || type.getKind().isFloat()
            : "Only support INT or FLOAT constants";
    }

    public static IntConst createIntConstant(int value) {
        if (value == 0) {
            return Constant.INT_0;
        } else {
            return new IntConst(value);
        }
    }

    public static FloatConst createFloatConstant(float value) {
        if (value == 0.0f) {
            // TODO: 考虑浮点误差?
            return Constant.FLOAT_0;
        } else {
            return new FloatConst(value);
        }
    }

    public static BoolConst getBoolConstant(boolean bool) {
        return bool ? BoolConst.trueBoolConst : BoolConst.falseBoolConst;
    }

    public static final IntConst INT_0 = new IntConst(0);
    public static final FloatConst FLOAT_0 = new FloatConst(0.0f);

    public static Constant getZeroByType(IRType type) {
        if (type.isInt()) {
            return INT_0;
        } else if (type.isFloat()) {
            return FLOAT_0;
        } else if (type instanceof ArrayIRTy) {
            return createZeroArrayConst(type);
        } else {
            throw new RuntimeException("Unknown type");
        }
    }

    public static ArrayConst createArrayConst(List<Constant> elements) {
        return new ArrayConst(elements);
    }

    public static ZeroArrayConst createZeroArrayConst(IRType type) {
        return new ZeroArrayConst(type);
    }
}
