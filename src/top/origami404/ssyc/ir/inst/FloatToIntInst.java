package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.type.IRType;
import top.origami404.ssyc.utils.Log;

public class FloatToIntInst extends Instruction {
    public FloatToIntInst(Value from) {
        super(InstKind.F2I, IRType.IntTy);
        Log.ensure(from.getType().equals(IRType.FloatTy));

        super.addOperandCO(from);
    }

    public Value getFrom() {
        return getOperand(0);
    }
}
