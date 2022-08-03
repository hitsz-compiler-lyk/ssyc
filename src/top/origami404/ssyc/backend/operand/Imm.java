package top.origami404.ssyc.backend.operand;

public abstract class Imm extends Operand {

    public Imm(opType s) {
        super(s);
    }

    public abstract String toHexString();
}
