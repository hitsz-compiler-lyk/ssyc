package backend.lir.operand;

import java.util.Objects;

public class Addr extends Operand {
    public Addr(String label) {
        super(OperandKind.Addr);
        this.label = label;
    }

    private final String label;

    @Override
    public String toString() {
        return label;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Addr addr && label.equals(addr.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(label);
    }
}
