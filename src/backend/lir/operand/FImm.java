package backend.lir.operand;

public class FImm extends Imm {
    private final float imm;

    public FImm(float imm) {
        super(OperandKind.FImm);
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
    public String toString() {
        return "#" + imm;
    }
}
