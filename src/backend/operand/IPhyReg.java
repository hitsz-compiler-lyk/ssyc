package backend.operand;

import java.util.HashMap;
import java.util.Map;

import utils.Log;

public class IPhyReg extends Reg {
    private String name;

    private static final Map<Integer, String> idNameMap = new HashMap<>() {
        {
            for (int i = 0; i <= 12; i++) {
                put(i, "r" + i);
            }
            put(13, "sp");
            put(14, "lr");
            put(15, "pc");
            put(16, "cspr");
        }
    };

    private static final Map<String, Integer> nameIdMap = new HashMap<>() {
        {
            for (int i = 0; i <= 15; i++) {
                put("r" + i, i);
            }
            put("sp", 13);
            put("lr", 14);
            put("pc", 15);
            put("cspr", 16);
        }
    };

    public IPhyReg(opType s) {
        super(s);
    }

    public IPhyReg(int n) {
        super(opType.IPhy, n);
        this.name = idNameMap.get(n);
        Log.ensure(this.name != null);
    }

    public IPhyReg(String name) {
        super(opType.IPhy);
        Log.ensure(nameIdMap.get(name) != null);
        this.setId(nameIdMap.get(name));
        this.name = name;
    }

    public boolean isCallerSave() {
        return this.getId() < 4;
    }

    public boolean isSpecial() {
        return this.getId() == 13 || this.getId() >= 15;
    }

    public boolean isCalleeSave() {
        return !this.isCallerSave() && !this.isSpecial();
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
