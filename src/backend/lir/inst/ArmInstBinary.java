package backend.lir.inst;

import backend.lir.ArmBlock;
import backend.lir.ArmShift;
import backend.lir.operand.Operand;

// 0: dst RegDef
// 1: lhs RegUse
// 2: rhs RegUse
public class ArmInstBinary extends ArmInst {
    ArmShift shift;

    public ArmInstBinary(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstBinary(ArmBlock block, ArmInstKind inst, Operand dst, Operand lhs, Operand rhs) {
        super(inst);
        block.asElementView().add(this);
        this.initOperands(dst, lhs, rhs);
        this.shift = null;
    }

    public ArmInstBinary(ArmBlock block, ArmInstKind inst, Operand dst, Operand lhs, Operand rhs, ArmCondType cond) {
        super(inst);
        block.asElementView().add(this);
        this.setCond(cond);
        this.initOperands(dst, lhs, rhs);
        this.shift = null;
    }

    public ArmInstBinary(ArmInstKind inst, Operand dst, Operand lhs, Operand rhs) {
        super(inst);
        this.initOperands(dst, lhs, rhs);
        this.shift = null;
    }

    public ArmInstBinary(ArmInstKind inst, Operand dst, Operand lhs, Operand rhs, ArmCondType cond) {
        super(inst);
        this.setCond(cond);
        this.initOperands(dst, lhs, rhs);
        this.shift = null;
    }

    public Operand getDst() {
        return this.getOperand(0);
    }

    public Operand getLhs() {
        return this.getOperand(1);
    }

    public Operand getRhs() {
        return this.getOperand(2);
    }

    public void setShift(ArmShift shift) {
        this.shift = shift;
    }

    public ArmShift getShift() {
        return shift;
    }
}
