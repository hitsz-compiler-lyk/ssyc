package backend.lir.inst;

import backend.lir.operand.IImm;
import backend.lir.operand.Operand;

public abstract class ArmInstAddr extends ArmInst {
    protected ArmInstAddr(ArmInstKind kind) {
        super(kind);
    }

    abstract public Operand getDst();

    abstract public IImm getOffset();

    abstract public Operand getAddr();

    abstract public void setTrueOffset(IImm trueOffset);

    abstract public void replaceAddr(Operand addr);
}
