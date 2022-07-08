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
    public void setLabel(String label) {
        super.setLabel(label);
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public String toHexString() {
        return Integer.toHexString(imm);
    }

    @Override
    public String toString() {
        if (label != "") {
            return "#" + Integer.toString(imm);
        } else {
            return label;
        }
    }
}
