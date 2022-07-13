package top.origami404.ssyc.backend.arm;

import top.origami404.ssyc.backend.operand.Operand;
import top.origami404.ssyc.utils.Log;

public class ArmInstUnary extends ArmInst {
    private Operand dst, src;

    public ArmInstUnary(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstUnary(ArmBlock block, ArmInstKind inst, Operand dst, Operand src) {
        super(inst);
        this.dst = dst;
        this.src = src;
        block.asElementView().add(this);
    }

    public ArmInstUnary(ArmBlock block, ArmInstKind inst, Operand dst, Operand src, ArmCondType cond) {
        super(inst);
        this.dst = dst;
        this.src = src;
        block.asElementView().add(this);
        this.setCond(cond);
    }

    @Override
    public String toString() {
        if (getInst() == ArmInstKind.INeg) {
            return "\t" + "neg" + getCond().toString() + "\t" + dst.toString() + "\t" + src.toString();
        } else if (getInst() == ArmInstKind.FNeg) {
            return "\t" + "vneg.f32" + getCond().toString() + "\t" + dst.toString() + "\t" + src.toString();
        } else {
            Log.ensure(false);
            return "";
        }
    }
}
