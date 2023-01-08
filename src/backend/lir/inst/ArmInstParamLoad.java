package backend.lir.inst;

import backend.lir.ArmBlock;
import backend.lir.operand.IImm;
import backend.lir.operand.IPhyReg;
import backend.lir.operand.Operand;

public class ArmInstParamLoad extends ArmInst {
    IImm trueOffset;

    public ArmInstParamLoad(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstParamLoad(ArmBlock block, Operand dst, IImm offset) {
        super(ArmInstKind.ParamLoad);
        block.asElementView().add(this);
        this.initOperands(dst, IPhyReg.SP, offset);
        this.trueOffset = null;
    }

    public ArmInstParamLoad(Operand dst, IImm offset) {
        super(ArmInstKind.ParamLoad);
        this.initOperands(dst, IPhyReg.SP, offset);
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
}
