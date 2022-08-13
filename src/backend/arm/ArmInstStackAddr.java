package backend.arm;

import backend.codegen.CodeGenManager;
import backend.operand.IImm;
import backend.operand.IPhyReg;
import backend.operand.Operand;
import utils.Log;

public class ArmInstStackAddr extends ArmInst {
    IImm trueOffset;
    boolean isFix;
    boolean isCAlloc;
    int nether, upper;

    public ArmInstStackAddr(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstStackAddr(ArmBlock block, Operand dst, IImm offset) {
        super(ArmInstKind.StackAddr);
        block.asElementView().add(this);
        this.initOperands(dst, new IPhyReg("sp"), offset);
        if (CodeGenManager.checkEncodeImm(Math.abs(offset.getImm()))) {
            this.setPrintCnt(1);
        } else {
            this.setPrintCnt(3);
        }
        this.trueOffset = null;
        this.isFix = false;
        this.nether = 0;
        this.upper = 2048;
    }

    public ArmInstStackAddr(Operand dst, IImm offset) {
        super(ArmInstKind.StackAddr);
        this.initOperands(dst, new IPhyReg("sp"), offset);
        if (CodeGenManager.checkEncodeImm(Math.abs(offset.getImm()))) {
            this.setPrintCnt(1);
        } else {
            this.setPrintCnt(3);
        }
        this.trueOffset = null;
        this.isFix = false;
        this.nether = 0;
        this.upper = 2040;
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

    public int getUpper() {
        return upper;
    }

    public int getNether() {
        return nether;
    }

    public void setUpper(int upper) {
        this.upper = upper;
    }

    public void setNether(int nether) {
        this.nether = nether;
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

    public boolean isLegalModify(int modify) {
        return nether <= modify && modify <= upper;
    }

    public void modifyRange(int modify) {
        Log.ensure(isLegalModify(modify), "illegal modify");
        this.nether -= modify;
        this.upper -= modify;
    }

    public void replaceOffset(Operand addr) {
        this.replaceOperand(2, addr);
    }

    @Override
    public String print() {
        var dst = getDst();
        var src = getSrc();
        var offset = getOffset();
        if (trueOffset != null) {
            offset = trueOffset;
        }
        int imm = Math.abs(offset.getImm());
        if (!isCAlloc) {
            Log.ensure((imm % 1024) == 0, "offset must be %1024 ==0");
        }
        String op = "add";
        if (offset.getImm() < 0) {
            op = "sub";
        }
        if (CodeGenManager.checkEncodeImm(imm)) {
            return "\t" + op + getCond().toString() + "\t" + dst.print() + ",\t" + src.print() + ",\t#"
                    + imm + "\n";
        } else {
            var move = new ArmInstMove(dst, new IImm(imm));
            move.setCond(getCond());
            return move.print() +
                    "\t" + op + getCond().toString() + "\t" + dst.print() + ",\t" + src.print() + ",\t" + dst.print()
                    + "\n";
        }
    }

}
