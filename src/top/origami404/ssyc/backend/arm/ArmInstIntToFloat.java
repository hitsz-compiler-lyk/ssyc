package top.origami404.ssyc.backend.arm;

import top.origami404.ssyc.backend.operand.Operand;

// 0: dst RegDef
// 1: drc RegUse
public class ArmInstIntToFloat extends ArmInst {

    public ArmInstIntToFloat(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstIntToFloat(ArmBlock block, Operand dst, Operand src) {
        super(ArmInstKind.IntToFloat);
        block.asElementView().add(this);
        this.initOperands(dst, src);
    }

    public Operand getDst() {
        return this.getOperand(0);
    }

    public Operand getSrc() {
        return this.getOperand(1);
    }

    @Override
<<<<<<< HEAD
    public String print() {
        return "\t" + "vcvt.f32.s32" + "\t" + getDst().print() + "\t" + getSrc().print();
=======
    public String toString() {
        return "\t" + "vcvt.f32.s32" + "\t" + getDst() + "\t" + getSrc();
>>>>>>> dfac878edd9308c304b5b7283c261c24dbc74992
    }

}
