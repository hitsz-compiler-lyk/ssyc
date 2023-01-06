package backend.lir.inst;

import backend.lir.ArmBlock;
import backend.lir.operand.Operand;
import utils.Log;

// 0: dst RegDef
// 1: drc RegUse
public class ArmInstUnary extends ArmInst {

    public ArmInstUnary(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstUnary(ArmBlock block, ArmInstKind inst, Operand dst, Operand src) {
        super(inst);
        block.asElementView().add(this);
        this.initOperands(dst, src);
        this.setPrintCnt(1);
    }

    public ArmInstUnary(ArmBlock block, ArmInstKind inst, Operand dst, Operand src, ArmCondType cond) {
        super(inst);
        block.asElementView().add(this);
        this.setCond(cond);
        this.initOperands(dst, src);
        this.setPrintCnt(1);
    }

    public Operand getDst() {
        return this.getOperand(0);
    }

    public Operand getSrc() {
        return this.getOperand(1);
    }
}
