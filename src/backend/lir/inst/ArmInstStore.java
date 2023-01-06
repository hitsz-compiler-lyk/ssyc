package backend.lir.inst;

import backend.lir.ArmBlock;
import backend.lir.ArmShift;
import backend.lir.operand.Addr;
import backend.lir.operand.IImm;
import backend.lir.operand.Operand;
import utils.Log;

// Store 都是 RegUse
// 0: src     RegUse
// 1: addr    RegUse
// 2: offset  RegUse
public class ArmInstStore extends ArmInst {
    ArmShift shift;

    public ArmInstStore(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstStore(ArmBlock block, Operand src, Operand addr) {
        super(ArmInstKind.Store);
        block.asElementView().add(this);
        this.initOperands(src, addr, new IImm(0));
        this.setPrintCnt(1);
        this.shift = null;
    }

    public ArmInstStore(ArmBlock block, Operand src, Operand addr, ArmCondType cond) {
        super(ArmInstKind.Store);
        block.asElementView().add(this);
        this.setCond(cond);
        this.initOperands(src, addr, new IImm(0));
        this.setPrintCnt(1);
        this.shift = null;
    }

    public ArmInstStore(ArmBlock block, Operand src, Operand addr, Operand offset) {
        super(ArmInstKind.Store);
        block.asElementView().add(this);
        this.initOperands(src, addr, offset);
        this.setPrintCnt(1);
        this.shift = null;
    }

    public ArmInstStore(ArmBlock block, Operand src, Operand addr, Operand offset, ArmCondType cond) {
        super(ArmInstKind.Store);
        block.asElementView().add(this);
        this.setCond(cond);
        this.initOperands(src, addr, offset);
        this.setPrintCnt(1);
        this.shift = null;
    }

    public ArmInstStore(Operand src, Operand addr) {
        super(ArmInstKind.Store);
        this.initOperands(src, addr, new IImm(0));
        this.setPrintCnt(1);
        this.shift = null;
    }

    public ArmInstStore(Operand src, Operand addr, ArmCondType cond) {
        super(ArmInstKind.Store);
        this.setCond(cond);
        this.initOperands(src, addr, new IImm(0));
        this.setPrintCnt(1);
        this.shift = null;
    }

    public ArmInstStore(Operand src, Operand addr, Operand offset) {
        super(ArmInstKind.Store);
        this.initOperands(src, addr, offset);
        this.setPrintCnt(1);
        this.shift = null;
    }

    public ArmInstStore(Operand src, Operand addr, int offset) {
        super(ArmInstKind.Store);
        this.initOperands(src, addr, new IImm(offset));
        this.setPrintCnt(1);
        this.shift = null;
    }

    public ArmInstStore(Operand src, Operand addr, Operand offset, ArmCondType cond) {
        super(ArmInstKind.Store);
        this.setCond(cond);
        this.initOperands(src, addr, offset);
        this.setPrintCnt(1);
        this.shift = null;
    }

    public Operand getSrc() {
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
        ;
    }

    public ArmShift getShift() {
        return shift;
    }

    public void setShift(ArmShift shift) {
        this.shift = shift;
    }
}
