package backend.lir.operand;

import java.util.Objects;

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

    @Override
    public boolean equals(Object obj) {
        return obj instanceof IImm iImm && imm == iImm.imm;
    }

    @Override
    public int hashCode() {
        return Objects.hash(imm);
    }
}
