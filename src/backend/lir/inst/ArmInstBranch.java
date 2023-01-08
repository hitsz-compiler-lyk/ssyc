package backend.lir.inst;

import backend.lir.ArmBlock;

public class ArmInstBranch extends ArmInst {
    private ArmBlock targetBlock;

    public ArmInstBranch(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstBranch(ArmBlock block, ArmBlock targetBlock) {
        super(ArmInstKind.Branch);
        this.targetBlock = targetBlock;
        block.asElementView().add(this);
    }

    public ArmInstBranch(ArmBlock targetBlock) {
        super(ArmInstKind.Branch);
        this.targetBlock = targetBlock;
    }


    public ArmInstBranch(ArmBlock block, ArmBlock targetBlock, ArmCondType cond) {
        super(ArmInstKind.Branch);
        this.targetBlock = targetBlock;
        block.asElementView().add(this);
        this.setCond(cond);
    }

    public ArmInstBranch(ArmBlock targetBlock, ArmCondType cond) {
        super(ArmInstKind.Branch);
        this.targetBlock = targetBlock;
        this.setCond(cond);
    }

    public ArmBlock getTargetBlock() {
        return targetBlock;
    }

    public void setTargetBlock(ArmBlock targetBlock) {
        this.targetBlock = targetBlock;
    }
}
