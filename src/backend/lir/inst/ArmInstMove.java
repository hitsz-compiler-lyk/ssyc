package backend.lir.inst;

import backend.lir.ArmBlock;
import backend.lir.ArmShift;
import backend.lir.operand.Operand;

// 0: dst RegUse
// 1: drc RegUse
public class ArmInstMove extends ArmInst {
    private ArmShift shift;

    public ArmInstMove(ArmBlock block, Operand dst, Operand src) {
        this(block, dst, src, ArmCondType.Any);
    }

    public ArmInstMove(Operand dst, Operand src) {
        this(dst, src, ArmCondType.Any);
    }

    public ArmInstMove(ArmBlock block, Operand dst, Operand src, ArmCondType cond) {
        this(dst, src, cond);
        block.add(this);
    }

    public ArmInstMove(Operand dst, Operand src, ArmCondType cond) {
        super(ArmInstKind.MOV);
        this.setCond(cond);
        this.initOperands(dst, src);
        this.shift = null;
    }

    public Operand getDst() {
        return getOperand(0);
    }

    public Operand getSrc() {
        return getOperand(1);
    }

    public void setShift(ArmShift shift) {
        this.shift = shift;
    }

    public ArmShift getShift() {
        return shift;
    }
}
