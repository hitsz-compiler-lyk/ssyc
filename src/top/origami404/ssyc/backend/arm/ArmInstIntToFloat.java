package top.origami404.ssyc.backend.arm;

import top.origami404.ssyc.backend.operand.Operand;

public class ArmInstIntToFloat extends ArmInst {
    private Operand dst, src;

    public ArmInstIntToFloat(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstIntToFloat(ArmBlock block, Operand dst, Operand src) {
        super(ArmInstKind.IntToFloat);
        this.dst = dst;
        this.src = src;
        block.asElementView().add(this);
    }

    @Override
    public String toString() {
        return "\t" + "vcvt.f32.s32" + "\t" + dst.toString() + "\t" + src.toString();
    }

}
