package backend.lir.inst;

import backend.lir.operand.IImm;
import backend.lir.operand.IPhyReg;
import backend.lir.operand.Operand;

public class ArmInstStackLoad extends ArmInstAddr {
    private IImm trueOffset;

    public ArmInstStackLoad(Operand dst, IImm offset) {
        super(ArmInstKind.StackLoad);
        this.initOperands(dst, IPhyReg.SP, offset);
        this.trueOffset = null;
    }

    public Operand getDst() { return getOperand(0); }
    public Operand getAddr() { return getOperand(1); }
    public IImm getOffset() { return (IImm) getOperand(2); }

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
