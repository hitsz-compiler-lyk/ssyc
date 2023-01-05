package backend.lir.operand;

public abstract class Operand {
    public enum OperandKind {
        IVirtual,
        IPhy,
        FVirtual,
        FPhy,
        IImm,
        FImm,
        Addr,
    }

    private final OperandKind kind;

    public Operand(OperandKind kind) {
        this.kind = kind;
    }

    public boolean isImm() {
        return kind == OperandKind.IImm || kind == OperandKind.FImm;
    }

    public boolean isInt() {
        return kind == OperandKind.IImm || kind == OperandKind.IPhy || kind == OperandKind.IVirtual;
    }

    public boolean isFloat() {
        return kind == OperandKind.FImm || kind == OperandKind.FPhy || kind == OperandKind.FVirtual;
    }

    public boolean isVirtual() {
        return kind == OperandKind.IVirtual || kind == OperandKind.FVirtual;
    }

    public boolean isPhy() {
        return kind == OperandKind.IPhy || kind == OperandKind.FPhy;
    }

    public boolean isReg() {
        return !isImm() && kind != OperandKind.Addr;
    }

    public OperandKind getKind() {
        return kind;
    }

    public String print() {
        return toString();
    }
}
