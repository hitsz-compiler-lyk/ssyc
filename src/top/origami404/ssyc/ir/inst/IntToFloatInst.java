package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.type.IRType;
import top.origami404.ssyc.utils.Log;

public class IntToFloatInst extends Instruction {
    public IntToFloatInst(Value from) {
        super(InstKind.I2F, IRType.IntTy);
        Log.ensure(from.getType().equals(IRType.IntTy));

        this.from = from;
        super.addOperandCO(from);
    }

    public Value getFrom() {
        return from;
    }

    private Value from;
}
