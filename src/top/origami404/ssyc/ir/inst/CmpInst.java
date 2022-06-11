package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.Value;

public class CmpInst extends Instruction {
    CmpInst(InstKind cmpKind, Value lhs, Value rhs) {
        super(cmpKind, lhs.getType());
        this.lhs = lhs;
        this.rhs = rhs;
        super.addOperand(lhs);
        super.addOperand(rhs);


        final var resultTyKind = lhs.getType().getKind();
        assert lhs.getType() == rhs.getType()
            : "lhs should have the same type as rhs";
        assert resultTyKind.isInt() &&resultTyKind.isFloat()
            : "BinOpInst require type INT or FLOAT";
        assert (resultTyKind.isInt() && cmpKind.isInt()) || (resultTyKind.isFloat() && cmpKind.isFloat())
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
