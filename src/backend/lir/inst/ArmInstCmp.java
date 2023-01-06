package backend.lir.inst;

import backend.lir.ArmBlock;
import backend.lir.operand.Operand;

// 0: lhs RegUse
// 1: rhs RegUse
public class ArmInstCmp extends ArmInst {
    private boolean isCmn = false;

    public ArmInstCmp(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstCmp(ArmBlock block, Operand lhs, Operand rhs, ArmCondType cond) {
        super(ArmInstKind.Cmp);
        block.asElementView().add(this);
        this.setCond(cond);
        this.initOperands(lhs, rhs);
        if (lhs.isFloat() || rhs.isFloat()) {
            this.setPrintCnt(2);
        } else {
            this.setPrintCnt(1);
        }
        this.isCmn = false;
    }

    public Operand getLhs() {
        return this.getOperand(0);
    }

    public Operand getRhs() {
        return this.getOperand(1);
    }

    public void setCmn(boolean isCmn) {
        this.isCmn = isCmn;
    }

    public boolean isCmn() {
        return isCmn;
    }
}
