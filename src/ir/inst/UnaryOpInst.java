package ir.inst;

import ir.IRVerifyException;
import ir.Value;
import ir.constant.Constant;

public class UnaryOpInst extends Instruction {
    public UnaryOpInst(InstKind opKind, Value arg) {
        super(opKind, arg.getType());
        super.addOperandCO(arg);
    }

    public Value getArg() {
        return getOperand(0);
    }

    @Override
    public void verify() throws IRVerifyException {
        super.verify();

        final var arg = getArg();
        final var thisType = getType();
        final var argType = arg.getType();

        ensure(thisType.isInt() || thisType.isFloat(),
                "Type of a UnaryOp must either be Int or be Float");
        ensure((getKind().isInt() && thisType.isInt()) || (getKind().isFloat()) && thisType.isFloat(),
                "Type of a UnaryOp must match its kind");
        ensure(thisType.equals(argType),
                "Type of LHS/RHS must be the same with the UnaryOp");
        ensure(getKind().isUnary(), "Unmatched kind");

        ensureNot(arg instanceof Constant,
                "A UnaryOp shouldn't have a constant as its argument");
    }
}
