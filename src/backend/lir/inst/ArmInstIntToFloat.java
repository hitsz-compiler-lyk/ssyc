package backend.lir.inst;

import backend.lir.ArmBlock;
import backend.lir.operand.Operand;

// 0: dst RegDef
// 1: drc RegUse
public class ArmInstIntToFloat extends ArmInst {
    public ArmInstIntToFloat(ArmBlock block, Operand dst, Operand src) {
        super(ArmInstKind.IntToFloat);
        block.add(this);
        this.initOperands(dst, src);
    }

    public Operand getDst() { return getOperand(0); }
    public Operand getSrc() { return getOperand(1); }
}
