package backend.lir.operand;

import utils.Log;

import java.util.*;

public class FPhyReg extends Reg {
    public static FPhyReg S(int n) {
        Log.ensure(0 <= n && n <= 31, "only s0 - s31 exists");
        return Objects.requireNonNull(phyRegs.get(n));
    }

    public static Set<FPhyReg> getFloatAllocatableRegs() {
        return allocatableRegs;
    }

    public boolean isCallerSave() {
        return id < 16;
    }

    public boolean isCalleeSave() {
        return id >= 16;
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public String toString() {
        return "s" + id;
    }

    private final static Map<Integer, FPhyReg> phyRegs = new HashMap<>() {
        {
            for (int i = 0; i < 32; i++) {
                put(i, new FPhyReg(i));
            }
        }
    };

    private static final Set<FPhyReg> allocatableRegs =
        Collections.unmodifiableSet(new LinkedHashSet<>(phyRegs.values()));

    private FPhyReg(int id) {
        super(OperandKind.FPhy);
        this.id = id;
    }

    private final int id;
}
