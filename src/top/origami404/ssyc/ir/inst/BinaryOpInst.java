package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.Value;

public class BinaryOpInst extends Instruction {
    public BinaryOpInst(InstKind opKind, Value lhs, Value rhs) {
        super(opKind, lhs.getType());

        this.lhs = lhs;
        this.rhs = rhs;
        super.addOperandCO(lhs);
        super.addOperandCO(rhs);


        final var resultTyKind = lhs.getType().getKind();
        assert lhs.getType() == rhs.getType()
            : "lhs should have the same type as rhs";
        assert resultTyKind.isInt() &&resultTyKind.isFloat()
            : "BinOpInst require type INT or FLOAT";
        assert (resultTyKind.isInt() && opKind.isInt()) || (resultTyKind.isFloat() && opKind.isFloat())
            : "OpKind type is unmatch with operand type";

    }

    public Value getLHS() {
        return lhs;
    }

    public Value getRHS() {
        return rhs;
    }

    private Value lhs;
    private Value rhs;
}
