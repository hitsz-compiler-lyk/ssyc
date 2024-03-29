package ir.constant;

import ir.Value;
import ir.constant.ArrayConst.ZeroArrayConst;
import ir.type.ArrayIRTy;
import ir.type.IRType;
import utils.Log;

import java.util.List;

public abstract class Constant extends Value {
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

    public boolean isZero() {
        return false;
    }

    public static ArrayConst createArrayConst(List<Constant> elements) {
        Log.ensure(!elements.isEmpty(), "Array constant should be empty");

        if (elements.stream().allMatch(Constant::isZero)) {
            final var type = IRType.createArrayTy(elements.size(), elements.get(0).getType());
            return createZeroArrayConst(type);
        } else {
            return new ArrayConst(elements);
        }
    }

    public static ZeroArrayConst createZeroArrayConst(IRType type) {
        return new ZeroArrayConst(type);
    }
}
