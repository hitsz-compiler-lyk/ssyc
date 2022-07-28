package top.origami404.ssyc.backend.operand;

public class Addr extends Operand {
    private String label;
    private boolean isGlobal;

    public Addr(opType s) {
        super(s);
    }

    public Addr(String label) {
        super(opType.Addr);
        this.label = label;
        this.isGlobal = false;
    }

    public Addr(String label, boolean isGlobal) {
        super(opType.Addr);
        this.label = label;
        this.isGlobal = isGlobal;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public void setGlobal(boolean isGlobal) {
        this.isGlobal = isGlobal;
    }

    public String getLabel() {
        return label;
    }

    public boolean isGlobal() {
        return isGlobal;
    }

    @Override
    public String print() {
        return label;
    }
}
