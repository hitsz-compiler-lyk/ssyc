package top.origami404.ssyc.backend.operand;

public class FImm extends Imm {
    float imm;

    public FImm(float imm) {
        super(opType.FImm);
        this.imm = imm;
    }

    public float getImm() {
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
        return Float.toHexString(imm);
    }

    @Override
    public String toString() {
        if (label != "") {
            return "#" + Float.toString(imm);
        } else {
            return label;
        }
    }
}
