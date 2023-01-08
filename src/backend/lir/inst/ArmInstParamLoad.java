package backend.lir.inst;

import backend.lir.ArmBlock;
import backend.lir.operand.IImm;
import backend.lir.operand.IPhyReg;
import backend.lir.operand.Operand;

public class ArmInstParamLoad extends ArmInst {
    private IImm trueOffset;

    public ArmInstParamLoad(ArmBlock block, Operand dst, IImm offset) {
        this(dst, offset);
        block.add(this);
    }

    public ArmInstParamLoad(Operand dst, IImm offset) {
        super(ArmInstKind.ParamLoad);
        this.initOperands(dst, IPhyReg.SP, offset);
        this.trueOffset = null;
    }

    public Operand getDst() { return getOperand(0); }
    public Operand getAddr() { return getOperand(1); }
    public IImm getOffset() { return (IImm) getOperand(2); }

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
