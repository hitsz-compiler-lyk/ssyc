package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.Value;

public class UnaryOpInst extends Instruction {
    public UnaryOpInst(InstKind opKind, Value arg) {
        super(opKind, arg.getType());
        super.addOperandCO(arg);
    }

    public Value getArg() {
        return getOperand(0);
    }
}
