package ir.inst;

import ir.IRVerifyException;
import ir.Value;
import ir.constant.Constant;
import ir.type.IRType;
import utils.Log;

public class IntToFloatInst extends Instruction {
    public IntToFloatInst(Value from) {
        super(InstKind.I2F, IRType.FloatTy);
        Log.ensure(from.getType().equals(IRType.IntTy));

        super.addOperandCO(from);
    }

    public Value getFrom() {
        return getOperand(0);
    }

    @Override
    public void verify() throws IRVerifyException {
        super.verify();

        final var from = getFrom();
        ensure(from.getType().isInt(), "Type of argument of I2F must be Int");
        ensureNot(from instanceof Constant, "Argument of I2F shouldn't be a constants");
    }
}
