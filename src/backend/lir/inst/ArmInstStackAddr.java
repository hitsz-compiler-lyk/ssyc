package backend.lir.inst;

import backend.lir.ArmBlock;
import backend.lir.operand.IImm;
import backend.lir.operand.IPhyReg;
import backend.lir.operand.Operand;
import utils.Log;

import java.util.Objects;

public class ArmInstStackAddr extends ArmInst {
    private IImm trueOffset;
    private boolean isFix;
    private boolean isCAlloc;

    public ArmInstStackAddr(ArmBlock block, Operand dst, IImm offset) {
        this(dst, offset);
        block.add(this);
    }

    public ArmInstStackAddr(Operand dst, IImm offset) {
        super(ArmInstKind.StackAddr);
        this.initOperands(dst, IPhyReg.SP, offset);
        this.trueOffset = null;
        this.isFix = false;
    }

    public Operand getDst() {
        return getOperand(0);
    }

    public Operand getSrc() {
        return getOperand(1);
    }

    public IImm getOffset() {
        return (IImm) getOperand(2);
    }

    public int getIntOffset() {
        return Objects.requireNonNullElse(trueOffset, getOffset()).getImm();
    }

    public boolean isCAlloc() {
        return isCAlloc;
    }

    public void setCAlloc(boolean isCAlloc) {
        this.isCAlloc = isCAlloc;
    }

    public void setTrueOffset(IImm trueOffset) {
        this.trueOffset = trueOffset;
        if (trueOffset != null && !isCAlloc) {
            Log.ensure((trueOffset.getImm() % 1024) == 0, "offset must be %1024 ==0");
        }
    }

    public boolean isFix() {
        return isFix;
    }

    public void setFix(boolean isFix) {
        this.isFix = isFix;
    }

    public IImm getTrueOffset() {
        return trueOffset;
    }

    public void replaceOffset(Operand addr) {
        this.replaceOperand(2, addr);
    }
}
