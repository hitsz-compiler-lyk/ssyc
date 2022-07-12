package top.origami404.ssyc.backend.arm;

public class ArmInstCall extends ArmInst {
    private ArmFunction func;

    public ArmInstCall(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstCall(ArmBlock block, ArmFunction func) {
        super(ArmInstKind.Call);
        this.func = func;
        block.asElementView().add(this);
    }

    public void setFunc(ArmFunction func) {
        this.func = func;
    }

    @Override
    public String toString() {
        return "\t" + "bl" + "\t" + func.getName() + "\n";
    }
}
