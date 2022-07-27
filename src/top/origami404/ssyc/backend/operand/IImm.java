package top.origami404.ssyc.backend.operand;

public class IImm extends Imm {
    int imm;

    public IImm(int imm) {
        super(opType.IImm);
        this.imm = imm;
    }

    public int getImm() {
        return imm;
    }

    @Override
    public String toHexString() {
        return Integer.toHexString(imm);
    }

    @Override
    public String print() {
        return "#" + Integer.toString(imm);
    }
}
