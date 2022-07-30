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

    @Override
    public String print() {
        String ret = "\t" + "b" + getCond().toString() + "\t" + targetBlock.getLabel() + "\n";
        if (getCond().equals(ArmCondType.Any)) {
            ret += ".ltorg\n";
        }
        return ret;
    }

}
