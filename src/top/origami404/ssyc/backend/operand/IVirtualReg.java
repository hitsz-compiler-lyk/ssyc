package top.origami404.ssyc.backend.operand;

public class IVirtualReg extends Reg {
    private static int cnt = 0;
    private int id = 0;

    public IVirtualReg(opType s) {
        super(s);
    }

    public IVirtualReg() {
        super(opType.IVirtual);
        this.id = cnt++;
    }

    public IVirtualReg(String label, boolean isGlobal) {
        super(opType.IVirtual);
        super.setGlobal(isGlobal);
        super.setLabel(label);
        this.id = cnt++;
    }

    @Override
    public String toString() {
        if (label != "") {
            return "@IV_" + label;
        }
        return "@IV" + Integer.toString(id);
    }
}
