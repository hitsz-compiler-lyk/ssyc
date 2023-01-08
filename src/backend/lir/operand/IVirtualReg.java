package backend.lir.operand;

import java.util.Objects;

public class IVirtualReg extends Reg {
    private static int cnt = 0;

    private final int id;

    public IVirtualReg() {
        super(OperandKind.IVirtual);
        this.id = cnt++;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof IVirtualReg reg && id == reg.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "@IV" + id;
    }
}
