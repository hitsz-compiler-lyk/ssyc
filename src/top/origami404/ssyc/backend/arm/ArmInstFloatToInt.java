package top.origami404.ssyc.backend.arm;

import top.origami404.ssyc.backend.operand.Operand;

public class ArmInstFloatToInt extends ArmInst {
    private Operand dst, src;

    public ArmInstFloatToInt(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstFloatToInt(ArmBlock block, Operand dst, Operand src) {
        super(ArmInstKind.FloatToInt);
        this.dst = dst;
        this.src = src;
        block.asElementView().add(this);
        this.addRegDef(this.dst);
        this.addRegUse(this.src);
    }

    @Override
    public String toString() {
        return "\t" + "vcvt.s32.f32" + "\t" + dst.toString() + "\t" + src.toString();
    }

}
