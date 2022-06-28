package top.origami404.ssyc.backend.operand;

public class Imm extends Operand {
    int imm;

    public int getImm() {
        return imm;
    }

    public Imm(opType s) {
        super(s);
        this.imm = 0;
    }

    public Imm(int imm) {
        super(opType.Imm);
        this.imm = imm;
    }

    @Override
    public String toString() {
        return "#" + Integer.toString(imm);
    }

}
