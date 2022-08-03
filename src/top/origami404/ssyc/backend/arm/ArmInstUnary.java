package top.origami404.ssyc.backend.arm;

import top.origami404.ssyc.backend.operand.Operand;
import top.origami404.ssyc.utils.Log;

// 0: dst RegDef
// 1: drc RegUse
public class ArmInstUnary extends ArmInst {

    public ArmInstUnary(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstUnary(ArmBlock block, ArmInstKind inst, Operand dst, Operand src) {
        super(inst);
        block.asElementView().add(this);
        this.initOperands(dst, src);
        this.setPrintCnt(1);
    }

    public ArmInstUnary(ArmBlock block, ArmInstKind inst, Operand dst, Operand src, ArmCondType cond) {
        super(inst);
        block.asElementView().add(this);
        this.setCond(cond);
        this.initOperands(dst, src);
        this.setPrintCnt(1);
    }

    public Operand getDst() {
        return this.getOperand(0);
    }

    public Operand getSrc() {
        return this.getOperand(1);
    }

    @Override
    public String print() {
        var dst = getDst();
        var src = getSrc();

        if (getInst() == ArmInstKind.INeg) {
            return "\t" + "neg" + getCond().toString() + "\t" + dst.print() + ",\t" + src.print() + "\n";
        } else if (getInst() == ArmInstKind.FNeg) {
            return "\t" + "vneg.f32" + getCond().toString() + "\t" + dst.print() + ",\t" + src.print() + "\n";
        } else {
            Log.ensure(false);
            return "";
        }
    }
}
