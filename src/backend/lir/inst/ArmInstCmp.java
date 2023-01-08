package backend.lir.inst;

import backend.lir.ArmBlock;
import backend.lir.operand.Operand;

// 0: lhs RegUse
// 1: rhs RegUse
public class ArmInstCmp extends ArmInst {
    private boolean isCmn = false;

    public ArmInstCmp(ArmBlock block, Operand lhs, Operand rhs, ArmCondType cond) {
        super(ArmInstKind.Cmp);
        block.add(this);
        this.setCond(cond);
        this.initOperands(lhs, rhs);
    }

    public Operand getLhs() { return getOperand(0); }
    public Operand getRhs() { return getOperand(1); }

    public void setCmn(boolean isCmn) {
        this.isCmn = isCmn;
    }

    public boolean isCmn() {
        return isCmn;
    }
}
