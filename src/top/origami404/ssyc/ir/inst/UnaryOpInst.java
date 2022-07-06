package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.Value;

public class UnaryOpInst extends Instruction {
    public UnaryOpInst(InstKind opKind, Value arg) {
        super(opKind, arg.getType());
        super.addOperandCO(arg);

        final var resultTyKind = arg.getType().getKind();
        assert resultTyKind.isInt() || resultTyKind.isFloat()
            : "BinOpInst require type INT or FLOAT";
        assert (resultTyKind.isInt() && opKind.isInt()) || (resultTyKind.isFloat() && opKind.isFloat())
            : "OpKind type is unmatch with operand type";
    }

    public Value getArg() {
        return getOperand(0);
    }
}
