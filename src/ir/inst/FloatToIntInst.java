package ir.inst;

import ir.IRVerifyException;
import ir.Value;
import ir.constant.Constant;
import ir.type.IRType;
import utils.Log;

public class FloatToIntInst extends Instruction {
    public FloatToIntInst(Value from) {
        super(InstKind.F2I, IRType.IntTy);
        Log.ensure(from.getType().equals(IRType.FloatTy));

        super.addOperandCO(from);
    }

    public Value getFrom() {
        return getOperand(0);
    }

    @Override
    public void verify() throws IRVerifyException {
        super.verify();

        final var from = getFrom();
        ensure(from.getType().isFloat(), "Type of argument of F2I must be Float");
        ensureNot(from instanceof Constant, "Argument of F2I shouldn't be a constants");
    }
}
