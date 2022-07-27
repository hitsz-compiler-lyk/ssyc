package top.origami404.ssyc.backend.operand;

import java.util.Objects;

public abstract class Operand {
    public enum opType {
        IVirtual,
        IPhy,
        FVirtual,
        FPhy,
        IImm,
        FImm,
        Addr,
    }

    opType s;

    public opType getState() {
        return s;
    }

    public Operand(opType s) {
        this.s = s;
    }

    public boolean IsIVirtual() {
        return s == opType.IVirtual;
    }

    public boolean IsIPhy() {
        return s == opType.IPhy;
    }

    public boolean IsFVirtual() {
        return s == opType.FVirtual;
    }

    public boolean IsFPhy() {
        return s == opType.FPhy;
    }

    public boolean IsIImm() {
        return s == opType.IImm;
    }

    public boolean IsFImm() {
        return s == opType.FImm;
    }

    public boolean IsImm() {
        return s == opType.IImm || s == opType.FImm;
    }

    public boolean IsInt() {
        return s == opType.IImm || s == opType.IPhy || s == opType.IVirtual;
    }

    public boolean IsFloat() {
        return s == opType.FImm || s == opType.FPhy || s == opType.FVirtual;
    }

    public boolean IsAddr() {
        return s == opType.Addr;
    }

    public boolean IsVirtual() {
        return s == opType.IVirtual || s == opType.FVirtual;
    }

    public boolean IsPhy() {
        return !IsVirtual();
    }

    public boolean IsReg() {
        return !IsImm() && s != opType.Addr;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Operand)) {
            return false;
        }
        if (((Operand) obj).getState() != this.s) {
            return false;
        }
        return switch (this.getState()) {
            case IImm -> ((IImm) obj).getImm() == ((IImm) this).getImm();
            case FImm -> ((FImm) obj).getImm() == ((FImm) this).getImm();
            case Addr -> ((Addr) obj).getLabel().equals(((Addr) this).getLabel())
                        && ((Addr) obj).isGlobal() == ((Addr) this).isGlobal();
            case IPhy, FPhy, IVirtual, FVirtual -> ((Reg) obj).getId() == ((Reg) this).getId();
            default -> this.equals(obj);
        };
    }

    @Override
    public int hashCode() {
        return switch (this.getState()) {
            case IImm -> Objects.hash(s, ((IImm) this).getImm());
            case FImm -> Objects.hash(s, ((FImm) this).getImm());
            case Addr -> Objects.hash(s, ((Addr) this).getLabel(), ((Addr) this).isGlobal());
            case IPhy, FPhy, IVirtual, FVirtual -> Objects.hash(s, ((Reg) this).getId());
            default -> super.hashCode();
        };
    }

    public abstract String print();
}
