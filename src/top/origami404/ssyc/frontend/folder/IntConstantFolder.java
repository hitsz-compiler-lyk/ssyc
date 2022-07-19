package top.origami404.ssyc.frontend.folder;

import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.constant.BoolConst;
import top.origami404.ssyc.ir.constant.Constant;
import top.origami404.ssyc.ir.constant.IntConst;
import top.origami404.ssyc.ir.inst.BinaryOpInst;
import top.origami404.ssyc.ir.inst.BoolToIntInst;
import top.origami404.ssyc.ir.inst.FloatToIntInst;
import top.origami404.ssyc.ir.inst.UnaryOpInst;

public class IntConstantFolder {
    public static Value tryFoldConst(Value value) {
        if (canFold(value)) {
            return foldConst(value);
        } else {
            return value;
        }
    }

    public static IntConst foldConst(Value value) {
        return Constant.createIntConstant(foldInt(value));
    }

    public static int foldInt(Value value) {
        if (value instanceof IntConst) {
            final var cst = (IntConst) value;
            return cst.getValue();
        } else if (value instanceof BinaryOpInst) {
            final var binop = (BinaryOpInst) value;
            final var lhs = binop.getLHS();
            final var rhs = binop.getRHS();
            return switch (binop.getKind()) {
                case IAdd -> foldInt(lhs) + foldInt(rhs);
                case ISub -> foldInt(lhs) - foldInt(rhs);
                case IMul -> foldInt(lhs) * foldInt(rhs);
                case IDiv -> foldInt(lhs) / foldInt(rhs);
                case IMod -> foldInt(lhs) % foldInt(rhs);
                default ->
                    throw new RuntimeException("Unfoldable value");
            };
        } else if (value instanceof UnaryOpInst) {
            final var uop = (UnaryOpInst) value;
            final var arg = uop.getArg();
            return switch (uop.getKind()) {
                case INeg -> - foldInt(arg);
                default -> {
                    throw new RuntimeException("Unfoldable value");
                }
            };
        } else if (value instanceof FloatToIntInst) {
            final var f2i = (FloatToIntInst) value;
            final var from = FloatConstantFolder.foldFloat(f2i.getFrom());
            return (int) from; // 若超出 int 范围, SysY 行为未定义, 就直接用 Java 的行为了
        } else if (value instanceof BoolToIntInst) {
            final var from = ((BoolToIntInst) value).getFrom();
            final var bool = ((BoolConst) from).getValue();
            return bool ? 1 : 0;
        } else {
            throw new RuntimeException("Unfoldable value");
        }
    }

    public static boolean canFold(Value value) {
        if (value instanceof IntConst) {
            return true;
        } else if (value instanceof BinaryOpInst) {
            final var inst = (BinaryOpInst) value;
            return inst.getKind().isInt() && canFold(inst.getLHS()) && canFold(inst.getRHS());
        } else if (value instanceof UnaryOpInst) {
            final var inst = (UnaryOpInst) value;
            return inst.getKind().isInt() && canFold(inst.getArg());
        } else if (value instanceof FloatToIntInst) {
            final var inst = (FloatToIntInst) value;
            return FloatConstantFolder.canFold(inst.getFrom());
        } else if (value instanceof BoolToIntInst) {
            return ((BoolToIntInst) value).getFrom() instanceof BoolConst;
        } else {
            return false;
        }
    }
}
