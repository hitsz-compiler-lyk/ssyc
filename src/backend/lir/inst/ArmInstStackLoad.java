package backend.lir.inst;

import backend.lir.ArmBlock;
import backend.codegen.CodeGenManager;
import backend.lir.operand.IImm;
import backend.lir.operand.IPhyReg;
import backend.lir.operand.Operand;
import utils.Log;

public class ArmInstStackLoad extends ArmInst {
    IImm trueOffset;

    public ArmInstStackLoad(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstStackLoad(ArmBlock block, Operand dst, IImm offset) {
        super(ArmInstKind.StackLoad);
        block.asElementView().add(this);
        this.initOperands(dst, IPhyReg.SP, offset);
        this.setPrintCnt(1);
        this.trueOffset = null;
    }

    public ArmInstStackLoad(Operand dst, IImm offset) {
        super(ArmInstKind.StackLoad);
        this.initOperands(dst, IPhyReg.SP, offset);
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

    public IImm getTrueOffset() {
        return trueOffset;
    }

    public void setTrueOffset(IImm trueOffset) {
        this.trueOffset = trueOffset;
    }

    public void replaceAddr(Operand addr) {
        this.replaceOperand(1, addr);
    }
}
