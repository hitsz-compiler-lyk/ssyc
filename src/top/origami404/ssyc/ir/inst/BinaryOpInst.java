package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.Value;

public class BinaryOpInst extends Instruction {
    public BinaryOpInst(InstKind opKind, Value lhs, Value rhs) {
        super(opKind, lhs.getType());

        super.addOperandCO(lhs);
        super.addOperandCO(rhs);
        
        final var resultTyKind = lhs.getType().getKind();
        assert lhs.getType() == rhs.getType()
            : "lhs should have the same type as rhs";
        assert resultTyKind.isInt() || resultTyKind.isFloat()
            : "BinOpInst require type INT or FLOAT";
        assert (resultTyKind.isInt() && opKind.isInt()) || (resultTyKind.isFloat() && opKind.isFloat())
            : "OpKind type is unmatch with operand type";

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
