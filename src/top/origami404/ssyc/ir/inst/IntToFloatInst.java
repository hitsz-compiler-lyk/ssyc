package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.Value;
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
}
