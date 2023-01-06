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
        this.setPrintCnt(1);
    }

    public ArmInstBranch(ArmBlock targetBlock) {
        super(ArmInstKind.Branch);
        this.targetBlock = targetBlock;
        this.setPrintCnt(1);
    }


    public ArmInstBranch(ArmBlock block, ArmBlock targetBlock, ArmCondType cond) {
        super(ArmInstKind.Branch);
        this.targetBlock = targetBlock;
        block.asElementView().add(this);
        this.setCond(cond);
        if (cond.equals(ArmCondType.Any)) {
            this.setPrintCnt(2);
        } else {
            this.setPrintCnt(1);
        }
    }

    public ArmInstBranch(ArmBlock targetBlock, ArmCondType cond) {
        super(ArmInstKind.Branch);
        this.targetBlock = targetBlock;
        this.setCond(cond);
        if (cond.equals(ArmCondType.Any)) {
            this.setPrintCnt(2);
        } else {
            this.setPrintCnt(1);
        }
    }

    public ArmBlock getTargetBlock() {
        return targetBlock;
    }

    public void setTargetBlock(ArmBlock targetBlock) {
        this.targetBlock = targetBlock;
    }
}
