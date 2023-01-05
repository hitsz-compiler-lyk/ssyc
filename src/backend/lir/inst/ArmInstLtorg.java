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

    @Override
    public String print() {
        String ret = "\tb\t" + label + "\n";
        ret += ".ltorg\n";
        ret += label + ":\n";
        return ret;
    }

}
