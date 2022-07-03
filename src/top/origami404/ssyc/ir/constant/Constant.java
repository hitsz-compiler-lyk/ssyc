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

    public static IntConst createIntConstant(int value) { return new IntConst(value); }
    public static FloatConst createFloatConstant(float value) { return new FloatConst(value); }
    public static BoolConst getBoolConstant(boolean bool) {
        return bool ? BoolConst.trueBoolConst : BoolConst.falseBoolConst;
    }

    public static final IntConst INT_0 = createIntConstant(0);
    public static final FloatConst FLOAT_0 = createFloatConstant(0.0f);

    public static Constant getZeroByType(IRType type) {
        if (type.equals(IRType.IntTy)) {
            return INT_0;
        } else if (type.equals(IRType.FloatTy)) {
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
