package backend.lir.inst;

import backend.lir.ArmBlock;
import backend.codegen.CodeGenManager;
import backend.lir.operand.IImm;
import backend.lir.operand.IPhyReg;

public class ArmInstReturn extends ArmInst {

    public ArmInstReturn(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstReturn(ArmBlock block) {
        super(ArmInstKind.Return);
        block.asElementView().add(this);
        this.setPrintCnt(7);
    }
}
