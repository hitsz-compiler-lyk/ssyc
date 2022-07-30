package top.origami404.ssyc.backend.arm;

import top.origami404.ssyc.backend.operand.IImm;
import top.origami404.ssyc.backend.operand.Operand;

// Store 都是 RegUse
// 0: src     RegUse
// 1: addr    RegUse
// 2: offset  RegUse
public class ArmInstStroe extends ArmInst {
    boolean isFixOffset = false;
    Operand trueOffset;

    public ArmInstStroe(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstStroe(ArmBlock block, Operand src, Operand addr) {
        super(ArmInstKind.Store);
        block.asElementView().add(this);
        this.initOperands(src, addr, new IImm(0));
        this.setPrintCnt(1);
    }

    public ArmInstStroe(ArmBlock block, Operand src, Operand addr, ArmCondType cond) {
        super(ArmInstKind.Store);
        block.asElementView().add(this);
        this.setCond(cond);
        this.initOperands(src, addr, new IImm(0));
        this.setPrintCnt(1);
    }

    public ArmInstStroe(ArmBlock block, Operand src, Operand addr, Operand offset) {
        super(ArmInstKind.Store);
        block.asElementView().add(this);
        this.initOperands(src, addr, offset);
        this.setPrintCnt(1);
    }

    public ArmInstStroe(ArmBlock block, Operand src, Operand addr, Operand offset, ArmCondType cond) {
        super(ArmInstKind.Store);
        block.asElementView().add(this);
        this.setCond(cond);
        this.initOperands(src, addr, offset);
        this.setPrintCnt(1);
    }

    public ArmInstStroe(Operand src, Operand addr) {
        super(ArmInstKind.Store);
        this.initOperands(src, addr, new IImm(0));
        this.setPrintCnt(1);
    }

    public ArmInstStroe(Operand src, Operand addr, ArmCondType cond) {
        super(ArmInstKind.Store);
        this.setCond(cond);
        this.initOperands(src, addr, new IImm(0));
        this.setPrintCnt(1);
    }

    public ArmInstStroe(Operand src, Operand addr, Operand offset) {
        super(ArmInstKind.Store);
        this.initOperands(src, addr, offset);
        this.setPrintCnt(1);
    }

    public ArmInstStroe(Operand src, Operand addr, Operand offset, ArmCondType cond) {
        super(ArmInstKind.Store);
        this.setCond(cond);
        this.initOperands(src, addr, offset);
        this.setPrintCnt(1);
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

    public boolean isFixOffset() {
        return isFixOffset;
    }

    public void setFixOffset(boolean isFixOffset) {
        this.isFixOffset = isFixOffset;
    }

    public void setTrueOffset(Operand trueOffset) {
        this.trueOffset = trueOffset;
    }

    public Operand getTrueOffset() {
        return trueOffset;
    }

    public void delTrueOffset() {
        this.trueOffset = null;
    }

    @Override
    public String print() {
        var src = getSrc();
        var addr = getAddr();
        var offset = getOffset();
        if (trueOffset != null) {
            offset = trueOffset;
        }

        var isVector = "";
        if (src.IsFloat()) {
            isVector = "v";
        }
        if (offset.equals(new IImm(0))) {
            return "\t" + isVector + "str" + getCond().toString() + "\t" + src.print() + ",\t[" + addr.print() + "]\n";
        } else {
            return "\t" + isVector + "str" + getCond().toString() + "\t" + src.print() + ",\t[" + addr.print()
                    + ",\t" + offset.print() + "]\n";
        }
    }
}
