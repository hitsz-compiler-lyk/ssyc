package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.IRVerifyException;
import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.type.IRType;

public class BoolToIntInst extends Instruction {
    public BoolToIntInst(Value from) {
        super(InstKind.B2I, IRType.IntTy);

        super.addOperandCO(from);
    }

    public Value getFrom() { return getOperand(0); }

    @Override
    public void verify() throws IRVerifyException {
        super.verify();
        ensure(getFrom().getType().isBool(), "BoolToInt must accept a Bool argument");
    }
}
