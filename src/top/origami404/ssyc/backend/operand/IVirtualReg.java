package top.origami404.ssyc.backend.operand;

public class IVirtualReg extends Reg {
    private static int cnt = 0;

    public IVirtualReg(opType s) {
        super(s);
    }

    public IVirtualReg() {
        super(opType.IVirtual, cnt++);
    }

    @Override
    public String print() {
        return "@IV" + Integer.toString(this.getId());
    }
}
