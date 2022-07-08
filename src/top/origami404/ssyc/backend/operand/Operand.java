package top.origami404.ssyc.backend.operand;

import top.origami404.ssyc.utils.Log;

public class Operand {
    public enum opType {
        IVirtual,
        IPhy,
        FVirtual,
        FPhy,
        IImm,
        FImm,
    }

    opType s;
    String label;
    boolean isGlobal;

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

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Operand)) {
            return false;
        }
        if (((Operand) obj).getState() != this.s) {
            return false;
        }
        if (this.getState() == opType.IImm) {
            return ((IImm) obj).getImm() == ((IImm) this).getImm();
        }
        if (this.getState() == opType.FImm) {
            return ((FImm) obj).getImm() == ((FImm) this).getImm();
        }
        return false;
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

    @Override
    public String toString() {
        Log.ensure(false);
        return "";
    }

}
