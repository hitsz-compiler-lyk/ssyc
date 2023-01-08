package backend.lir.inst;

import backend.lir.ArmBlock;

public class ArmInstReturn extends ArmInst {

    public ArmInstReturn(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstReturn(ArmBlock block) {
        super(ArmInstKind.Return);
        block.asElementView().add(this);
    }
}
