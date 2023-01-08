package backend.lir.inst;

public class ArmInstLiteralPoolPlacement extends ArmInst {
    private final String label;

    public ArmInstLiteralPoolPlacement(String label) {
        super(ArmInstKind.Ltorg);
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
