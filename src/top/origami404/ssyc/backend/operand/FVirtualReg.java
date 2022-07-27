package top.origami404.ssyc.backend.operand;

public class FVirtualReg extends Reg {
    private static int cnt = 0;

    public FVirtualReg(opType s) {
        super(s);
    }

    public FVirtualReg() {
        super(opType.FVirtual, cnt++);
    }

    @Override
    public String print() {
        return "@FV" + Integer.toString(this.getId());
    }

}
