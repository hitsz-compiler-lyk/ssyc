package ir.inst;

import ir.IRVerifyException;
import ir.Value;
import ir.constant.Constant;
import ir.type.IRType;

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
        ensure(getKind().isCmp(), "Unmatched kind");
        ensure(lhsType.equals(rhsType), "Type of LHS/RHS must be the same with the CmpInst");
        ensure((getKind().isInt() && lhsType.isInt()) || (getKind().isFloat() && lhsType.isFloat()),
                "Type of a CmpInst must match its kind");

        ensureNot(lhs instanceof Constant && rhs instanceof Constant,
                "A CmpInst shouldn't have two constants as its arguments");
    }
}
