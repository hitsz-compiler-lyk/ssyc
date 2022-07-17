package top.origami404.ssyc.backend.arm;

public class ArmInstReturn extends ArmInst {

    public ArmInstReturn(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstReturn(ArmBlock block) {
        super(ArmInstKind.Return);
        block.asElementView().add(this);
    }

    @Override
    public String toString() {
        return "\t" + "bx" + "\t" + "lr" + "\n";
    }

}
