package backend.lir.operand;

import java.util.Objects;

public class FVirtualReg extends Reg {
    private static int cnt = 0;

    private final int id;

    public FVirtualReg() {
        super(OperandKind.FVirtual);
        this.id = cnt++;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof FVirtualReg reg && id == reg.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "@FV" + id;
    }
}
