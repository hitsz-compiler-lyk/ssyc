package top.origami404.ssyc.backend.operand;

public class Operand implements Comparable<Operand> {
    public static final Operand ZeroImm = new Operand(0);

    public enum opType {
        IVirtual,
        IPhy,
        FVirtual,
        FPhy,
        Imm,
    }

    int imm;

    public int getImm() {
        return imm;
    }

    opType s;

    public opType getState() {
        return s;
    }

    public Operand(opType s) {
        this.s = s;
    }

    public Operand(int imm) {
        this.s = opType.Imm;
        this.imm = imm;
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

    public boolean IsImm() {
        return s == opType.Imm;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Operand)) {
            return false;
        }
        if (((Operand) obj).getState() != this.s) {
            return false;
        }
        if (this.getState() == opType.Imm) {
            return ((Operand) obj).getImm() == this.imm;
        } else {
            return true;
        }

    }

    @Override
    public int compareTo(Operand x) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public String toString() {
        return "#" + Integer.toString(imm);
    }

}
