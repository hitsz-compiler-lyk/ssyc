package top.origami404.ssyc.backend.arm;

import top.origami404.ssyc.backend.operand.IImm;
import top.origami404.ssyc.backend.operand.Operand;
import top.origami404.ssyc.utils.Log;

// 0: dst     RegDef
// 1: addr    RegUse
// 2: offset  RegUse
public class ArmInstLoad extends ArmInst {
    boolean isFixOffset = false;
    Operand trueOffset;

    public ArmInstLoad(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstLoad(ArmBlock block, Operand dst, Operand addr) {
        super(ArmInstKind.Load);
        block.asElementView().add(this);
        this.initOperands(dst, addr, new IImm(0));
        if (addr.IsAddr()) {
            this.setPrintCnt(2);
        } else {
            this.setPrintCnt(1);
        }
    }

    public ArmInstLoad(ArmBlock block, Operand dst, Operand addr, ArmCondType cond) {
        super(ArmInstKind.Load);
        block.asElementView().add(this);
        this.setCond(cond);
        this.initOperands(dst, addr, new IImm(0));
        if (addr.IsAddr()) {
            this.setPrintCnt(2);
        } else {
            this.setPrintCnt(1);
        }
    }

    public ArmInstLoad(Operand dst, Operand addr) {
        super(ArmInstKind.Load);
        this.initOperands(dst, addr, new IImm(0));
        if (addr.IsAddr()) {
            this.setPrintCnt(2);
        } else {
            this.setPrintCnt(1);
        }
    }

    public ArmInstLoad(Operand dst, Operand addr, ArmCondType cond) {
        super(ArmInstKind.Load);
        this.setCond(cond);
        this.initOperands(dst, addr, new IImm(0));
        if (addr.IsAddr()) {
            this.setPrintCnt(2);
        } else {
            this.setPrintCnt(1);
        }
    }

    public ArmInstLoad(ArmBlock block, Operand dst, Operand addr, Operand offset) {
        super(ArmInstKind.Load);
        block.asElementView().add(this);
        this.initOperands(dst, addr, offset);
        if (addr.IsAddr()) {
            this.setPrintCnt(2);
        } else {
            this.setPrintCnt(1);
        }
    }

    public ArmInstLoad(ArmBlock block, Operand dst, Operand addr, Operand offset, ArmCondType cond) {
        super(ArmInstKind.Load);
        block.asElementView().add(this);
        this.setCond(cond);
        this.initOperands(dst, addr, offset);
        if (addr.IsAddr()) {
            this.setPrintCnt(2);
        } else {
            this.setPrintCnt(1);
        }
    }

    public ArmInstLoad(Operand dst, Operand addr, Operand offset) {
        super(ArmInstKind.Load);
        this.initOperands(dst, addr, offset);
        if (addr.IsAddr()) {
            this.setPrintCnt(2);
        } else {
            this.setPrintCnt(1);
        }
    }

    public ArmInstLoad(Operand dst, Operand addr, Operand offset, ArmCondType cond) {
        super(ArmInstKind.Load);
        this.setCond(cond);
        this.initOperands(dst, addr, offset);
        if (addr.IsAddr()) {
            this.setPrintCnt(2);
        } else {
            this.setPrintCnt(1);
        }
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
        var dst = getDst();
        var addr = getAddr();
        var offset = getOffset();
        if (trueOffset != null) {
            offset = trueOffset;
        }

        var isVector = "";
        if (dst.IsFloat()) {
            isVector = "v";
        }

        if (addr.IsAddr()) {
            Log.ensure(!dst.IsFloat(), "load addr into vfp");
            // return "\tldr" + getCond().toString() + "\t" + dst.print() + ",\t=" +
            // addr.print() + "\n";
            return "\tmovw" + getCond().toString() + "\t" + dst.print() + ",\t:lower16:" + addr.print() + "\n" +
                    "\tmovt" + getCond().toString() + "\t" + dst.print() + ",\t:upper16:" + addr.print() + "\n";
        } else if (offset.equals(new IImm(0))) {
            return "\t" + isVector + "ldr" + getCond().toString() + "\t" + dst.print() + ",\t[" + addr.print() + "]\n";
        } else {
            return "\t" + isVector + "ldr" + getCond().toString() + "\t" + dst.print() + ",\t[" + addr.print()
                    + ",\t" + offset.print() + "]\n";
        }
    }

}
