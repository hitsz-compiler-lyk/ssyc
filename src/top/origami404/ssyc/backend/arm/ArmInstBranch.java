package top.origami404.ssyc.backend.arm;

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

    public ArmInstBranch(ArmBlock block, ArmBlock targetBlock, ArmCondType cond) {
        super(ArmInstKind.Branch);
        this.targetBlock = targetBlock;
        block.asElementView().add(this);
        this.setCond(cond);
    }

    @Override
    public String toString() {
        return "\t" + "b" + getCond().toString() + "\t" + targetBlock.getLabel() + "\n";
    }

}
