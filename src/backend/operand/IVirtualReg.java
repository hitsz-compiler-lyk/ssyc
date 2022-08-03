package backend.operand;

public class IVirtualReg extends Reg {
    private static int cnt = 0;

    public IVirtualReg(opType s) {
        super(s);
    }

    public IVirtualReg() {
        super(opType.IVirtual, cnt++);
    }

    public static int nowId(){
        return cnt;
    }

    @Override
    public String print() {
        return "@IV" + this.getId();
    }

    @Override
    public String toString() {
        return "@IV" + this.getId();
    }
}
