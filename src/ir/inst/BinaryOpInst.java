package ir.inst;

import ir.IRVerifyException;
import ir.Value;
import ir.constant.Constant;
import ir.constant.IntConst;

public class BinaryOpInst extends Instruction {
    public BinaryOpInst(InstKind opKind, Value lhs, Value rhs) {
        super(opKind, lhs.getType());

        super.addOperandCO(lhs);
        super.addOperandCO(rhs);
    }

    public Value getLHS() {
        return getOperand(0);
    }
    public Value getRHS() {
        return getOperand(1);
    }

    public Value replaceLHS(Value newLHS) { return replaceOperandCO(0, newLHS); }
    public Value replaceRHS(Value newRHS) { return replaceOperandCO(1, newRHS); }

    @Override
    public void verify() throws IRVerifyException {
        super.verify();

        final var lhs = getLHS();
        final var rhs = getRHS();

        final var thisType = getType();
        final var lhsType = lhs.getType();
        final var rhsType = rhs.getType();

        ensure(thisType.isInt() || thisType.isFloat(),
                "Type of a BinaryOp must either be Int or be Float");
        ensure((getKind().isInt() && thisType.isInt()) || (getKind().isFloat()) && thisType.isFloat(),
                "Type of a BinaryOp must match its kind");
        ensure(thisType.equals(lhsType) && thisType.equals(rhsType),
                "Type of LHS/RHS must be the same with the BinaryOp");
        ensure(getKind().isBinary(), "Unmatched kind");

        ensureNot(lhs instanceof Constant && rhs instanceof Constant,
                "A BinaryOp shouldn't have two constants as its arguments");

        if (getKind().equals(InstKind.IDiv)) {
            ensureNot(rhs instanceof IntConst && ((IntConst) rhs).getValue() == 0,
                    "An IDiv shouldn't have a constant 0 as its RHS");
        }
    }
}
