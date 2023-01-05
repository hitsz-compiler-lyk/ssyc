package backend.lir.operand;

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
}
