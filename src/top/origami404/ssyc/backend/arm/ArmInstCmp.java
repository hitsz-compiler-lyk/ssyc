package top.origami404.ssyc.backend.arm;

import top.origami404.ssyc.backend.operand.Operand;

public class ArmInstCmp extends ArmInst {
    private Operand lhs, rhs;

    public ArmInstCmp(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstCmp(ArmBlock block, Operand lhs, Operand rhs, ArmCondType cond) {
        super(ArmInstKind.Branch);
        this.lhs = lhs;
        this.rhs = rhs;
        block.asElementView().add(this);
        this.setCond(cond);
        this.addRegUse(this.lhs);
        this.addRegUse(this.rhs);
    }

    @Override
    public String toString() {
        var op = "cmp";
        var ret = "";
        if (lhs.IsFloat()) {
            op = "vcmp.f32";
        }
        ret += "\t" + op + "\t" + lhs.toString() + ",\t" + rhs.toString() + "\n";
        if (lhs.IsFloat()) {
            ret += "\tvmrs\tAPSR_nzcv,\tfpscr\n";
        }
        return ret;
    }

}
