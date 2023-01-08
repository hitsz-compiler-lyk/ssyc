package backend.lir.inst;

import backend.lir.ArmBlock;
import backend.lir.operand.IImm;
import backend.lir.operand.IPhyReg;
import backend.lir.operand.Operand;

public class ArmInstStackStore extends ArmInst {
    IImm trueOffset;

    public ArmInstStackStore(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstStackStore(ArmBlock block, Operand dst, IImm offset) {
        super(ArmInstKind.StackStore);
        block.asElementView().add(this);
        this.initOperands(dst, IPhyReg.SP, offset);
        this.trueOffset = null;
    }

    public ArmInstStackStore(Operand dst, IImm offset) {
        super(ArmInstKind.StackStore);
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
