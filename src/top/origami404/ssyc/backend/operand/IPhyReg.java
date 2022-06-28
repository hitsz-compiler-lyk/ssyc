package top.origami404.ssyc.backend.operand;

import java.util.HashMap;

public class IPhyReg extends Reg {
    private String name;

    private static final HashMap<Integer, String> nameMap = new HashMap<Integer, String>() {
        {
            for (int i = 0; i <= 12; i++) {
                put(i, "r" + Integer.toString(i));
            }
            put(13, "sp");
            put(14, "lr");
            put(15, "pc");
            put(16, "cspr");
        }
    };

    public IPhyReg(opType s) {
        super(s);
    }

    public IPhyReg(int n) {
        super(opType.IPhy);
        this.id = n;
        this.name = nameMap.get(n);
        assert this.name != null;
    }

    @Override
    public String toString() {
        return name;
    }

}
