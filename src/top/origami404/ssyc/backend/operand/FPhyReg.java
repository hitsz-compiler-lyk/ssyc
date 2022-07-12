package top.origami404.ssyc.backend.operand;

import java.util.HashMap;

import top.origami404.ssyc.utils.Log;

public class FPhyReg extends Reg {
    private String name;
    private int id;

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
        Log.ensure(this.name != null);
    }

    public int getId() {
        return id;
    }

    @Override
    public String toString() {
        return name;
    }


}
