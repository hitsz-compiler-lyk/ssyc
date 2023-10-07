package backend.lir.inst;

import backend.lir.ArmBlock;
import backend.lir.ArmShift;
import backend.lir.operand.IImm;
import backend.lir.operand.Operand;

// 0: dst     RegDef
// 1: addr    RegUse
// 2: offset  RegUse
public class ArmInstLoad extends ArmInst {
    private ArmShift shift;

    public ArmInstLoad(ArmBlock block, Operand dst, Operand addr) {
        this(dst, addr);
        block.add(this);
    }

    public ArmInstLoad(Operand dst, Operand addr) {
        this(dst, addr, new IImm(0), ArmCondType.Any);
    }

    public ArmInstLoad(Operand dst, Operand addr, Operand offset, ArmCondType cond) {
        super(ArmInstKind.Load);
        this.setCond(cond);
        this.initOperands(dst, addr, offset);
        this.shift = null;
    }

    public Operand getDst() { return getOperand(0); }
    public Operand getAddr() { return getOperand(1); }
    public Operand getOffset() { return getOperand(2); }

    public void replaceAddr(Operand op) {
        this.replaceOperand(1, op);
    }

    public void replaceOffset(Operand op) {
        this.replaceOperand(2, op);
    }

    public void replaceShift(ArmShift shift) { this.shift = shift; }

    public ArmShift getShift() {
        return shift;
    }
}
