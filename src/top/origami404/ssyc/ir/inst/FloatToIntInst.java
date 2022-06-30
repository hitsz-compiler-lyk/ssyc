package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.type.IRType;

public class FloatToIntInst extends Instruction {
    public FloatToIntInst(Value from) {
        super(InstKind.F2I, IRType.IntTy);
        assert from.getType().equals(IRType.FloatTy);

        this.from = from;
        super.addOperandCO(from);
    }

    public Value getFrom() {
        return from;
    }

    private Value from;
}
