package backend.lir.operand;

import utils.Log;

import java.util.*;

public class IPhyReg extends Reg {
    public static IPhyReg R(int n) {
        Log.ensure(0 <= n && n <= 15, "only r0 - r15 exists");
        return Objects.requireNonNull(phyRegs.get(n));
    }

    public static final IPhyReg SP = new IPhyReg(13);
    public static final IPhyReg LR = new IPhyReg(14);
    public static final IPhyReg PC = new IPhyReg(15);
    public static final IPhyReg CSPR = new IPhyReg(16);

    public static Set<IPhyReg> getIntAllocatableRegs() {
        return allocatableIRRegs;
    }

    public boolean isCallerSave() {
        return id < 4;
    }

    public boolean isSpecial() {
        return id == 13 || id == 15 || id == 16;
    }

    public boolean isCalleeSave() {
        return !isCallerSave() && !isSpecial();
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
    public String toString() {
        return switch (id) {
            case 13 -> "sp";
            case 14 -> "lr";
            case 15 -> "pc";
            case 16 -> "cspr";
            default -> "r" + id;
        };
    }

    private static final Map<Integer, IPhyReg> phyRegs =
        Collections.unmodifiableMap(new HashMap<>() {
        {
            for (int i = 0; i <= 12; i++) {
                put(i, new IPhyReg(i));
            }
            put(13, SP);
            put(14, LR);
            put(15, PC);
        }
    });

    private static final Set<IPhyReg> allocatableIRRegs =
        Collections.unmodifiableSet(new LinkedHashSet<>() {
        {
            for (int i = 0; i <= 12; i++) {
                add(R(i));
            }
            add(LR);
        }
    });

    private IPhyReg(int id) {
        super(OperandKind.IPhy);
        this.id = id;
    }

    private final int id;
}
