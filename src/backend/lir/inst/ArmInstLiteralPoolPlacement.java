package backend.lir.inst;

public class ArmInstLiteralPoolPlacement extends ArmInst {
    private String label;

    public ArmInstLiteralPoolPlacement(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstLiteralPoolPlacement(String label) {
        super(ArmInstKind.Ltorg);
        this.label = label;
        this.setPrintCnt(3);
    }

    public String getLabel() {
        return label;
    }
}
