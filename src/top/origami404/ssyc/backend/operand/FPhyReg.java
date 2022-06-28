package top.origami404.ssyc.backend.operand;

import java.util.HashMap;

public class FPhyReg extends Reg {
    private String name;

    private static final HashMap<Integer, String> nameMap = new HashMap<Integer, String>() {
        {
            for (int i = 0; i <= 7; i++) {
                put(i, "s" + Integer.toString(i));
            }
        }
    };

    public FPhyReg(opType s) {
        super(s);
    }

    public FPhyReg(int n) {
        super(opType.FPhy);
        this.id = n;
        this.name = nameMap.get(n);
        assert this.name != null;
    }

    @Override
    public String toString() {
        return name;
    }
}
