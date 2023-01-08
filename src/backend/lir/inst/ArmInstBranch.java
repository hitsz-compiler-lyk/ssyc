package backend.lir.inst;

import backend.lir.ArmBlock;

public class ArmInstBranch extends ArmInst {
    private final ArmBlock targetBlock;

    public ArmInstBranch(ArmBlock block, ArmBlock targetBlock) {
        this(block, targetBlock, ArmCondType.Any);
    }

    public ArmInstBranch(ArmBlock block, ArmBlock targetBlock, ArmCondType cond) {
        this(targetBlock, cond);
        block.add(this);
    }

    public ArmInstBranch(ArmBlock targetBlock, ArmCondType cond) {
        super(ArmInstKind.Branch);
        this.targetBlock = targetBlock;
        this.setCond(cond);
    }

    public ArmBlock getTargetBlock() {
        return targetBlock;
    }
}
