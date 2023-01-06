package backend.lir.inst;

public class ArmInstLtorg extends ArmInst {
    private String label;

    public ArmInstLtorg(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstLtorg(String label) {
        super(ArmInstKind.Ltorg);
        this.label = label;
        this.setPrintCnt(3);
    }

    public String getLabel() {
        return label;
    }
}
