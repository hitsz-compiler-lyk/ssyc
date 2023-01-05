package backend.lir.inst;

import backend.lir.ArmBlock;
import backend.codegen.CodeGenManager;
import backend.lir.operand.IImm;
import backend.lir.operand.IPhyReg;
import backend.lir.operand.Operand;
import utils.Log;

public class ArmInstParamLoad extends ArmInst {
    IImm trueOffset;

    public ArmInstParamLoad(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstParamLoad(ArmBlock block, Operand dst, IImm offset) {
        super(ArmInstKind.ParamLoad);
        block.asElementView().add(this);
        this.initOperands(dst, new IPhyReg("sp"), offset);
        this.setPrintCnt(1);
        this.trueOffset = null;
    }

    public ArmInstParamLoad(Operand dst, IImm offset) {
        super(ArmInstKind.ParamLoad);
        this.initOperands(dst, new IPhyReg("sp"), offset);
        this.setPrintCnt(1);
        this.trueOffset = null;
    }

    public Operand getDst() {
        return this.getOperand(0);
    }

    public Operand getAddr() {
        return this.getOperand(1);
    }

    public IImm getOffset() {
        return (IImm) this.getOperand(2);
    }

    public void setTrueOffset(IImm trueOffset) {
        this.trueOffset = trueOffset;
    }

    public IImm getTrueOffset() {
        return trueOffset;
    }

    public void replaceAddr(Operand addr) {
        this.replaceOperand(1, addr);
    }

    @Override
    public String print() {
        var dst = getDst();
        var addr = getAddr();
        var offset = trueOffset;
        Log.ensure(offset != null, "true offset must not be null");

        var isVector = "";
        if (dst.IsFloat()) {
            isVector = "v";
            Log.ensure(CodeGenManager.checkOffsetRange(offset.getImm(), true), "LoadParam offset illegal");
        } else {
            Log.ensure(CodeGenManager.checkOffsetRange(offset.getImm(), false), "LoadParam offset illegal");
        }

        if (offset.equals(new IImm(0))) {
            return "\t" + isVector + "ldr" + getCond().toString() + "\t" + dst.print() + ",\t[" + addr.print() + "]\n";
        }
        return "\t" + isVector + "ldr" + getCond().toString() + "\t" + dst.print() + ",\t[" + addr.print()
                + ",\t" + offset.print() + "]\n";
    }

}
