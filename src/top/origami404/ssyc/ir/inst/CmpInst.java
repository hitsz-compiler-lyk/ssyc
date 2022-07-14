package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.IRVerifyException;
import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.constant.Constant;
import top.origami404.ssyc.ir.type.IRType;

public class CmpInst extends Instruction {
    public CmpInst(InstKind cmpKind, Value lhs, Value rhs) {
        super(cmpKind, IRType.BoolTy);
        super.addOperandCO(lhs);
        super.addOperandCO(rhs);
    }

    public Value getLHS() { return getOperand(0); }
    public Value getRHS() { return getOperand(1); }

    @Override
    public void verify() throws IRVerifyException {
        super.verify();

        final var lhs = getLHS();
        final var rhs = getRHS();

        final var thisType = getType();
        final var lhsType = lhs.getType();
        final var rhsType = rhs.getType();

        ensure(thisType.isBool(),
                "Type of a CmpInst must be Bool");
        ensure((getKind().isInt() && thisType.isInt()) || (getKind().isFloat()) && thisType.isFloat(),
                "Type of a CmpInst must match its kind");
        ensure(thisType.equals(lhsType) && thisType.equals(rhsType),
                "Type of LHS/RHS must be the same with the CmpInst");
        ensure(getKind().isCmp(), "Unmatched kind");

        ensureNot(lhs instanceof Constant && rhs instanceof Constant,
                "A CmpInst shouldn't have two constants as its arguments");
    }
}
