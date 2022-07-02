package top.origami404.ssyc.frontend.folder;

import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.constant.Constant;
import top.origami404.ssyc.ir.constant.FloatConst;
import top.origami404.ssyc.ir.inst.BinaryOpInst;
import top.origami404.ssyc.ir.inst.IntToFloatInst;
import top.origami404.ssyc.ir.inst.UnaryOpInst;

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
        if (value instanceof FloatConst) {
            final var cst = (FloatConst) value;
            return cst.getValue();
        } else if (value instanceof BinaryOpInst) {
            final var binop = (BinaryOpInst) value;
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
        } else if (value instanceof UnaryOpInst) {
            final var uop = (UnaryOpInst) value;
            final var arg = uop.getArg();
            return switch (uop.getKind()) {
                case FNeg -> - foldFloat(arg);
                default -> {
                    throw new RuntimeException("Unfoldable value");
                }
            };
        } else if (value instanceof IntToFloatInst) {
            final var i2f = (IntToFloatInst) value;
            final var from = IntConstantFolder.foldInt(i2f.getFrom());
            return (float) from;
        } else {
            throw new RuntimeException("Unfoldable value");
        }
    }

    public static boolean canFold(Value value) {
        if (value instanceof FloatConst) {
            return true;
        } else if (value instanceof BinaryOpInst) {
            final var inst = (BinaryOpInst) value;
            return inst.getKind().isFloat() && canFold(inst.getLHS()) && canFold(inst.getRHS());
        } else if (value instanceof UnaryOpInst) {
            final var inst = (UnaryOpInst) value;
            return inst.getKind().isFloat() && canFold(inst.getArg());
        } else if (value instanceof IntToFloatInst) {
            final var inst = (IntToFloatInst) value;
            return IntConstantFolder.canFold(inst.getFrom());
        } else {
            return false;
        }
    }
}
