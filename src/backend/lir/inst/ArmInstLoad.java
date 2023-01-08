package backend.lir.inst;

import backend.lir.ArmBlock;
import backend.lir.ArmShift;
import backend.lir.operand.Addr;
import backend.lir.operand.IImm;
import backend.lir.operand.Operand;

// 0: dst     RegDef
// 1: addr    RegUse
// 2: offset  RegUse
public class ArmInstLoad extends ArmInst {
    ArmShift shift;

    public ArmInstLoad(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstLoad(ArmBlock block, Operand dst, Operand addr) {
        super(ArmInstKind.Load);
        block.asElementView().add(this);
        this.initOperands(dst, addr, new IImm(0));
        this.shift = null;
    }

    public ArmInstLoad(ArmBlock block, Operand dst, Operand addr, ArmCondType cond) {
        super(ArmInstKind.Load);
        block.asElementView().add(this);
        this.setCond(cond);
        this.initOperands(dst, addr, new IImm(0));
        this.shift = null;
    }

    public ArmInstLoad(Operand dst, Operand addr) {
        super(ArmInstKind.Load);
        this.initOperands(dst, addr, new IImm(0));
        this.shift = null;
    }

    public ArmInstLoad(Operand dst, Operand addr, ArmCondType cond) {
        super(ArmInstKind.Load);
        this.setCond(cond);
        this.initOperands(dst, addr, new IImm(0));
        this.shift = null;
    }

    public ArmInstLoad(ArmBlock block, Operand dst, Operand addr, Operand offset) {
        super(ArmInstKind.Load);
        block.asElementView().add(this);
        this.initOperands(dst, addr, offset);
        this.shift = null;
    }

    public ArmInstLoad(ArmBlock block, Operand dst, Operand addr, Operand offset, ArmCondType cond) {
        super(ArmInstKind.Load);
        block.asElementView().add(this);
        this.setCond(cond);
        this.initOperands(dst, addr, offset);
        this.shift = null;
    }

    public ArmInstLoad(Operand dst, Operand addr, Operand offset) {
        super(ArmInstKind.Load);
        this.initOperands(dst, addr, offset);
        this.shift = null;
    }

    public ArmInstLoad(Operand dst, Operand addr, int offset) {
        super(ArmInstKind.Load);
        this.initOperands(dst, addr, new IImm(offset));
        this.shift = null;
    }

    public ArmInstLoad(Operand dst, Operand addr, Operand offset, ArmCondType cond) {
        super(ArmInstKind.Load);
        this.setCond(cond);
        this.initOperands(dst, addr, offset);
        this.shift = null;
    }

    public Operand getDst() {
        return this.getOperand(0);
    }

    public Operand getAddr() {
        return this.getOperand(1);
    }

    public Operand getOffset() {
        return this.getOperand(2);
    }

    public void replaceAddr(Operand op) {
        this.replaceOperand(1, op);
    }

    public void replaceOffset(Operand op) {
        this.replaceOperand(2, op);
    }

    public ArmShift getShift() {
        return shift;
    }

    public void setShift(ArmShift shift) {
        this.shift = shift;
    }
}
