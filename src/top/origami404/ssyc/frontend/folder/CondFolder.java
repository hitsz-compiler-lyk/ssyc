package top.origami404.ssyc.frontend.folder;

import top.origami404.ssyc.ir.constant.BoolConst;
import top.origami404.ssyc.ir.constant.Constant;
import top.origami404.ssyc.ir.inst.CmpInst;

public class CondFolder {
    public static boolean canFold(CmpInst cond) {
        return cond.getKind().isInt()
            && IntConstantFolder.canFold(cond.getLHS())
            && IntConstantFolder.canFold(cond.getRHS());
    }

    public static BoolConst foldConst(CmpInst cond) {
        return Constant.getBoolConstant(foldBool(cond));
    }

    public static boolean foldBool(CmpInst cond) {
        final var lhs = IntConstantFolder.foldInt(cond.getLHS());
        final var rhs = IntConstantFolder.foldInt(cond.getRHS());

        // TODO: 考虑 FloatConstantFolder ?
        // 也许还需要考虑 32 位浮点运算在不同平台的表现

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
    }
}
