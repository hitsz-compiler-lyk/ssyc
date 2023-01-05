package backend.lir.operand;

public class IImm extends Imm {
    private final int imm;

    public IImm(int imm) {
        super(OperandKind.IImm);
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
    public String toString() {
        return "#" + imm;
    }
}
