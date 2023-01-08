package backend.lir.inst;

import backend.lir.ArmBlock;
import backend.lir.ArmShift;
import backend.lir.operand.Operand;

// 0: dst RegDef
// 1: lhs RegUse
// 2: rhs RegUse
public class ArmInstBinary extends ArmInst {
    private ArmShift shift;

    public ArmInstBinary(ArmBlock block, ArmInstKind kind, Operand dst, Operand lhs, Operand rhs) {
        this(kind, dst, lhs, rhs);
        block.add(this);
    }

    public ArmInstBinary(ArmInstKind kind, Operand dst, Operand lhs, Operand rhs) {
        this(kind, dst, lhs, rhs, ArmCondType.Any);
    }

    public ArmInstBinary(ArmInstKind kind, Operand dst, Operand lhs, Operand rhs, ArmCondType cond) {
        super(kind);
        this.setCond(cond);
        this.initOperands(dst, lhs, rhs);
        this.shift = null;
    }

    public Operand getDst() { return getOperand(0); }
    public Operand getLhs() { return getOperand(1); }
    public Operand getRhs() { return getOperand(2); }

    public void setShift(ArmShift shift) {
        this.shift = shift;
    }

    public ArmShift getShift() {
        return shift;
    }
}
