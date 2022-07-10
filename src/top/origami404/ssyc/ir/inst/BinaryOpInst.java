package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.Value;

public class BinaryOpInst extends Instruction {
    public BinaryOpInst(InstKind opKind, Value lhs, Value rhs) {
        super(opKind, lhs.getType());

        super.addOperandCO(lhs);
        super.addOperandCO(rhs);
    }

    public Value getLHS() {
        return getOperand(0);
    }
    public Value getRHS() {
        return getOperand(1);
    }

    public Value replaceLHS(Value newLHS) { return replaceOperandCO(0, newLHS); }
    public Value replaceRHS(Value newRHS) { return replaceOperandCO(1, newRHS); }
}
