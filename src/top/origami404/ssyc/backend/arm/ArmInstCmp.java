package top.origami404.ssyc.backend.arm;

import top.origami404.ssyc.backend.operand.Operand;

// 0: lhs RegUse
// 1: rhs RegUse
public class ArmInstCmp extends ArmInst {

    public ArmInstCmp(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstCmp(ArmBlock block, Operand lhs, Operand rhs, ArmCondType cond) {
        super(ArmInstKind.Cmp);
        block.asElementView().add(this);
        this.setCond(cond);
        this.initOperands(lhs, rhs);
        if (lhs.IsFloat() || rhs.IsFloat()) {
            this.setPrintCnt(2);
        } else {
            this.setPrintCnt(1);
        }
    }

    public Operand getLhs() {
        return this.getOperand(0);
    }

    public Operand getRhs() {
        return this.getOperand(1);
    }

    @Override
    public String print() {
        var lhs = getLhs();
        var rhs = getRhs();
        var op = "cmp";
        var ret = "";
        if (lhs.IsFloat() || rhs.IsFloat()) {
            op = "vcmp.f32";
        }
        ret += "\t" + op + "\t" + lhs.print() + ",\t" + rhs.print() + "\n";
        if (lhs.IsFloat() || rhs.IsFloat()) {
            ret += "\tvmrs\tAPSR_nzcv,\tfpscr\n";
        }
        return ret;
    }

}
