package top.origami404.ssyc.frontend.folder;

import top.origami404.ssyc.ir.constant.BoolConst;
import top.origami404.ssyc.ir.constant.Constant;
import top.origami404.ssyc.ir.inst.CmpInst;

public class CondFolder {
    public static boolean canFold(CmpInst cond) {
        final var canFoldInt = cond.getKind().isInt()
            && IntConstantFolder.canFold(cond.getLHS())
            && IntConstantFolder.canFold(cond.getRHS());

        final var canFoldFloat = cond.getKind().isFloat()
            && FloatConstantFolder.canFold(cond.getLHS())
            && FloatConstantFolder.canFold(cond.getRHS());

        return canFoldInt || canFoldFloat;
    }

    public static BoolConst foldConst(CmpInst cond) {
        return Constant.getBoolConstant(foldBool(cond));
    }

    public static boolean foldBool(CmpInst cond) {
        if (cond.getKind().isInt()) {
            final var lhs = IntConstantFolder.foldInt(cond.getLHS());
            final var rhs = IntConstantFolder.foldInt(cond.getRHS());

            return switch (cond.getKind()) {
                case ICmpEq -> lhs == rhs;
                case ICmpNe -> lhs != rhs;
                case ICmpGe -> lhs >= rhs;
                case ICmpGt -> lhs > rhs;
                case ICmpLe -> lhs <= rhs;
                case ICmpLt -> lhs < rhs;
                default ->
                    throw new RuntimeException("Unfoldable cond");
            };
        } else {
            final var lhs = FloatConstantFolder.foldFloat(cond.getLHS());
            final var rhs = FloatConstantFolder.foldFloat(cond.getRHS());

            return switch (cond.getKind()) {
                case FCmpEq -> lhs == rhs;
                case FCmpNe -> lhs != rhs;
                case FCmpGe -> lhs >= rhs;
                case FCmpGt -> lhs > rhs;
                case FCmpLe -> lhs <= rhs;
                case FCmpLt -> lhs < rhs;
                default ->
                    throw new RuntimeException("Unfoldable cond");
            };
        }
    }
}
