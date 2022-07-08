package top.origami404.ssyc.backend.operand;

public class FVirtualReg extends Reg {
    private static int cnt = 0;
    private int id = 0;

    public FVirtualReg(opType s) {
        super(s);
    }

    public FVirtualReg() {
        super(opType.FVirtual);
        this.id = cnt++;
    }

    public FVirtualReg(String label, boolean isGlobal) {
        super(opType.FVirtual);
        super.setGlobal(isGlobal);
        super.setLabel(label);
        this.id = cnt++;
    }

    @Override
    public String toString() {
        if (label != "") {
            return "@IV_" + label;
        }
        return "@FV" + Integer.toString(id);
    }

}
