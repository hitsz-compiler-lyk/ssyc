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
        return "0x" + Integer.toHexString(Float.floatToIntBits(imm));
    }

    @Override
    public String print() {
        return "#" + imm;
    }

    @Override
    public String toString() {
        return "#" + imm;
    }
}
