package frontend.folder;

import ir.Value;
import ir.constant.Constant;
import ir.constant.FloatConst;
import ir.inst.BinaryOpInst;
import ir.inst.IntToFloatInst;
import ir.inst.UnaryOpInst;

// TODO: 考虑不同平台浮点运算的误差问题
// 不过 Clang 似乎也没有考虑, 而且都是 IEEE, 应该算起来是一样的吧...
public class FloatConstantFolder {
    public static Value tryFoldValue(Value value) {
        if (canFold(value)) {
            return foldConst(value);
        } else {
            return value;
        }
    }

    public static FloatConst foldConst(Value value) {
        return Constant.createFloatConstant(foldFloat(value));
    }

    public static float foldFloat(Value value) {
        if (value instanceof final FloatConst cst) {
            return cst.getValue();
        } else if (value instanceof final BinaryOpInst binop) {
            final var lhs = binop.getLHS();
            final var rhs = binop.getRHS();
            return switch (binop.getKind()) {
                case FAdd -> foldFloat(lhs) + foldFloat(rhs);
                case FSub -> foldFloat(lhs) - foldFloat(rhs);
                case FMul -> foldFloat(lhs) * foldFloat(rhs);
                case FDiv -> foldFloat(lhs) / foldFloat(rhs);
                default ->
                    throw new RuntimeException("Unfoldable value");
            };
        } else if (value instanceof final UnaryOpInst uop) {
            final var arg = uop.getArg();
            return switch (uop.getKind()) {
                case FNeg -> - foldFloat(arg);
                default -> throw new RuntimeException("Unfoldable value");
            };
        } else if (value instanceof final IntToFloatInst i2f) {
            final var from = IntConstantFolder.foldInt(i2f.getFrom());
            return (float) from;
        } else {
            throw new RuntimeException("Unfoldable value");
        }
    }

    public static boolean canFold(Value value) {
        if (value instanceof FloatConst) {
            return true;
        } else if (value instanceof final BinaryOpInst inst) {
            return inst.getKind().isFloat() && canFold(inst.getLHS()) && canFold(inst.getRHS());
        } else if (value instanceof final UnaryOpInst inst) {
            return inst.getKind().isFloat() && canFold(inst.getArg());
        } else if (value instanceof final IntToFloatInst inst) {
            return IntConstantFolder.canFold(inst.getFrom());
        } else {
            return false;
        }
    }
}
