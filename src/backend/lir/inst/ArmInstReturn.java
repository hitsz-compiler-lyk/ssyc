package backend.lir.inst;

import backend.lir.ArmBlock;

public class ArmInstReturn extends ArmInst {
    public ArmInstReturn(ArmBlock block) {
        super(ArmInstKind.Return);
        block.add(this);
    }
}
