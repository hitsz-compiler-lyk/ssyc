package backend.lir.inst;

import backend.lir.ArmBlock;
import backend.lir.ArmShift;
import backend.lir.operand.IImm;
import backend.lir.operand.Operand;

// Store 都是 RegUse
// 0: src     RegUse
// 1: addr    RegUse
// 2: offset  RegUse
public class ArmInstStore extends ArmInst {
    private final ArmShift shift;

    public ArmInstStore(ArmBlock block, Operand src, Operand addr) {
        this(block, src, addr, new IImm(0));
    }

    public ArmInstStore(ArmBlock block, Operand src, Operand addr, Operand offset) {
        this(src, addr, offset, ArmCondType.Any);
        block.add(this);
    }

    public ArmInstStore(Operand src, Operand addr, Operand offset, ArmCondType cond) {
        super(ArmInstKind.Store);
        this.setCond(cond);
        this.initOperands(src, addr, offset);
        this.shift = null;
    }

    public Operand getSrc() { return getOperand(0); }
    public Operand getAddr() { return getOperand(1); }
    public Operand getOffset() { return getOperand(2); }

    public void replaceAddr(Operand op) {
        this.replaceOperand(1, op);
    }

    public void replaceOffset(Operand op) {
        this.replaceOperand(2, op);
        ;
    }

    public ArmShift getShift() {
        return shift;
    }
}
