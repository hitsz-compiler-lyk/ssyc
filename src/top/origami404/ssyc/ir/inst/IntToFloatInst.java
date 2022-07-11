package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.IRVerifyException;
import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.constant.Constant;
import top.origami404.ssyc.ir.type.IRType;
import top.origami404.ssyc.utils.Log;

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
