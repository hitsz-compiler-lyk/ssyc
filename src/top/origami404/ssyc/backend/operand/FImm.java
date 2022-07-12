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
    public String toHexString() {
        return Float.toHexString(imm);
    }

    @Override
    public String toString() {
        return "#" + Float.toString(imm);
    }
}
