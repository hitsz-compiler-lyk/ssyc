package backend.lir.operand;

import java.util.LinkedHashSet;
import java.util.Set;

public abstract class Reg extends Operand {
    public static Set<Reg> getAllAllocatableRegs() {
        final var result = new LinkedHashSet<Reg>();
        result.addAll(IPhyReg.getIntAllocatableRegs());
        result.addAll(FPhyReg.getFloatAllocatableRegs());
        return result;
    }

    public static int getAllocatableRegsCnt(boolean isInteger) {
        return isInteger ? IPhyReg.getIntAllocatableRegs().size() : FPhyReg.getFloatAllocatableRegs().size();
    }

    protected Reg(OperandKind s) {
        super(s);
    }
}
