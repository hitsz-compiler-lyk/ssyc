package backend.lir.inst;

import backend.lir.ArmBlock;
import backend.codegen.CodeGenManager;
import backend.lir.operand.IImm;
import backend.lir.operand.IPhyReg;
import backend.lir.operand.Operand;
import utils.Log;

public class ArmInstStackAddr extends ArmInst {
    IImm trueOffset;
    boolean isFix;
    boolean isCAlloc;

    public ArmInstStackAddr(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstStackAddr(ArmBlock block, Operand dst, IImm offset) {
        super(ArmInstKind.StackAddr);
        block.asElementView().add(this);
        this.initOperands(dst, IPhyReg.SP, offset);
        if (CodeGenManager.checkEncodeImm(Math.abs(offset.getImm()))) {
            this.setPrintCnt(1);
        } else {
            this.setPrintCnt(3);
        }
        this.trueOffset = null;
        this.isFix = false;
    }

    public ArmInstStackAddr(Operand dst, IImm offset) {
        super(ArmInstKind.StackAddr);
        this.initOperands(dst, IPhyReg.SP, offset);
        if (CodeGenManager.checkEncodeImm(Math.abs(offset.getImm()))) {
            this.setPrintCnt(1);
        } else {
            this.setPrintCnt(3);
        }
        this.trueOffset = null;
        this.isFix = false;
    }

    public Operand getDst() {
        return this.getOperand(0);
    }

    public Operand getSrc() {
        return this.getOperand(1);
    }

    public IImm getOffset() {
        return (IImm) this.getOperand(2);
    }

    public int getIntOffset() {
        if (trueOffset != null) {
            return trueOffset.getImm();
        }
        return getOffset().getImm();
    }

    public boolean isCAlloc() {
        return isCAlloc;
    }

    public void setCAlloc(boolean isCAlloc) {
        this.isCAlloc = isCAlloc;
    }

    public void setTrueOffset(IImm trueOffset) {
        this.trueOffset = trueOffset;
        if (trueOffset == null) {
            if (CodeGenManager.checkEncodeImm(Math.abs(getOffset().getImm()))) {
                this.setPrintCnt(1);
            } else {
                this.setPrintCnt(3);
            }
        } else {
            if (!isCAlloc) {
                Log.ensure((trueOffset.getImm() % 1024) == 0, "offset must be %1024 ==0");
            }
            if (CodeGenManager.checkEncodeImm(Math.abs(trueOffset.getImm()))) {
                this.setPrintCnt(1);
            } else {
                this.setPrintCnt(3);
            }
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
