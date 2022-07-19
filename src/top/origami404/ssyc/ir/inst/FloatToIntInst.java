package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.BasicBlock;
import top.origami404.ssyc.ir.IRVerifyException;
import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.constant.Constant;
import top.origami404.ssyc.ir.type.IRType;
import top.origami404.ssyc.utils.Log;

public class FloatToIntInst extends Instruction {
    public FloatToIntInst(BasicBlock block, Value from) {
        super(block, InstKind.F2I, IRType.IntTy);
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
