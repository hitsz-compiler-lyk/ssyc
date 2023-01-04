package backend.operand;

import utils.Log;

import java.util.HashMap;
import java.util.Map;

public class FPhyReg extends Reg {
    private String name;

    private static final Map<Integer, String> nameMap = new HashMap<>() {
        {
            for (int i = 0; i <= 31; i++) {
                put(i, "s" + i);
            }
        }
    };

    private static final Map<String, Integer> nameIdMap = new HashMap<>() {
        {
            for (int i = 0; i <= 31; i++) {
                put("s" + i, i);
            }
        }
    };

    public FPhyReg(opType s) {
        super(s);
    }

    public FPhyReg(int n) {
        super(opType.FPhy, n);
        this.name = nameMap.get(n);
        Log.ensure(this.name != null);
    }

    public FPhyReg(String name) {
        super(opType.FPhy);
        Log.ensure(nameIdMap.get(name) != null);
        this.setId(nameIdMap.get(name));
        this.name = name;
    }

    public boolean isCallerSave() {
        return this.getId() < 16;
    }

    public boolean isSpecial() {
        return false;
    }

    public boolean isCalleeSave() {
        return this.getId() >= 16;
    }

    @Override
    public String print() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
