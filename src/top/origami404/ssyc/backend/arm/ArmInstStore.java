package top.origami404.ssyc.backend.arm;

import top.origami404.ssyc.backend.operand.IImm;
import top.origami404.ssyc.backend.operand.Operand;
import top.origami404.ssyc.utils.Log;

// Store 都是 RegUse
// 0: src     RegUse
// 1: addr    RegUse
// 2: offset  RegUse
public class ArmInstStore extends ArmInst {
    boolean isFixOffset = false;
    boolean isStack = true;
    Operand trueOffset;
    ArmInstMove offsetMove;

    public ArmInstStore(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstStore(ArmBlock block, Operand src, Operand addr) {
        super(ArmInstKind.Store);
        block.asElementView().add(this);
        this.initOperands(src, addr, new IImm(0));
        this.setPrintCnt(1);
        this.isStack = true;
    }

    public ArmInstStore(ArmBlock block, Operand src, Operand addr, ArmCondType cond) {
        super(ArmInstKind.Store);
        block.asElementView().add(this);
        this.setCond(cond);
        this.initOperands(src, addr, new IImm(0));
        this.setPrintCnt(1);
        this.isStack = true;
    }

    public ArmInstStore(ArmBlock block, Operand src, Operand addr, Operand offset) {
        super(ArmInstKind.Store);
        block.asElementView().add(this);
        this.initOperands(src, addr, offset);
        this.setPrintCnt(1);
        this.isStack = true;
    }

    public ArmInstStore(ArmBlock block, Operand src, Operand addr, Operand offset, ArmCondType cond) {
        super(ArmInstKind.Store);
        block.asElementView().add(this);
        this.setCond(cond);
        this.initOperands(src, addr, offset);
        this.setPrintCnt(1);
        this.isStack = true;
    }

    public ArmInstStore(Operand src, Operand addr) {
        super(ArmInstKind.Store);
        this.initOperands(src, addr, new IImm(0));
        this.setPrintCnt(1);
        this.isStack = true;
    }

    public ArmInstStore(Operand src, Operand addr, ArmCondType cond) {
        super(ArmInstKind.Store);
        this.setCond(cond);
        this.initOperands(src, addr, new IImm(0));
        this.setPrintCnt(1);
        this.isStack = true;
    }

    public ArmInstStore(Operand src, Operand addr, Operand offset) {
        super(ArmInstKind.Store);
        this.initOperands(src, addr, offset);
        this.setPrintCnt(1);
        this.isStack = true;
    }

    public ArmInstStore(Operand src, Operand addr, int offset) {
        super(ArmInstKind.Store);
        this.initOperands(src, addr, new IImm(offset));
        this.setPrintCnt(1);
        this.isStack = true;
    }

    public ArmInstStore(Operand src, Operand addr, Operand offset, ArmCondType cond) {
        super(ArmInstKind.Store);
        this.setCond(cond);
        this.initOperands(src, addr, offset);
        this.setPrintCnt(1);
        this.isStack = true;
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

    public void replaceOffset(Operand offset) {
        this.replaceOperand(2, offset);
    }

    public void setOffsetMove(ArmInstMove offsetMove) {
        this.offsetMove = offsetMove;
    }

    public ArmInstMove getOffsetMove() {
        return offsetMove;
    }

    public void setStack(boolean isStack) {
        this.isStack = isStack;
    }

    public boolean isStack() {
        return isStack;
    }

    @Override
    public String print() {
        var src = getSrc();
        var addr = getAddr();
        var offset = getOffset();
        if (trueOffset != null) {
            offset = trueOffset;
        }
        Log.ensure(!addr.IsAddr(), "str a actual addr");

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
