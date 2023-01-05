package backend.lir.operand;

import utils.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class IPhyReg extends Reg {
    private final int id;

    public static final IPhyReg SP = new IPhyReg(13);
    public static final IPhyReg LR = new IPhyReg(14);
    public static final IPhyReg PC = new IPhyReg(15);
    public static final IPhyReg CSPR = new IPhyReg(16);

    private static final Map<Integer, IPhyReg> phyRegs = new HashMap<>() {
        {
            for (int i = 0; i <= 12; i++) {
                put(i, new IPhyReg(i));
            }
            put(13, SP);
            put(14, LR);
            put(15, PC);
        }
    };

    public static IPhyReg R(int n) {
        Log.ensure(0 <= n && n <= 15, "only r0 - r15 exists");
        return phyRegs.get(n);
    }

    private IPhyReg(int id) {
        super(opType.IPhy);
        this.id = id;
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

    public String getName() {
        return switch (id) {
            case 13 -> "sp";
            case 14 -> "lr";
            case 15 -> "pc";
            case 16 -> "cspr";
            default -> "r" + id;
        };
    }

    @Override
    public boolean equals(Object o) {
        // 没有创建新 IPhyReg 的方法, equals 的判断直接用 == 就可以了
        return o == this;
    }

    @Override
    public int hashCode() {
        // 没有创建新 IPhyReg 的方法, equals 的判断直接用指针类似物就可以了
        return System.identityHashCode(this);
    }

    @Override
    public String print() {
        return getName();
    }

    @Override
    public String toString() {
        return getName();
    }
}
