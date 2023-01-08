package backend.lir.inst;

import backend.lir.ArmBlock;
import backend.lir.operand.Operand;

// 0: dst RegDef
// 1: drc RegUse
public class ArmInstUnary extends ArmInst {
    public ArmInstUnary(ArmBlock block, ArmInstKind inst, Operand dst, Operand src) {
        super(inst);
        block.add(this);
        this.initOperands(dst, src);
    }

    public Operand getDst() { return getOperand(0); }
    public Operand getSrc() { return getOperand(1); }
}
