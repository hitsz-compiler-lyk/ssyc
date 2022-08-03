package backend.arm;

import backend.operand.Operand;

// 0: dst RegDef
// 1: drc RegUse
public class ArmInstFloatToInt extends ArmInst {

    public ArmInstFloatToInt(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstFloatToInt(ArmBlock block, Operand dst, Operand src) {
        super(ArmInstKind.FloatToInt);
        block.asElementView().add(this);
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
        return "\t" + "vcvt.s32.f32" + "\t" + getDst().print() + ",\t" + getSrc().print() + "\n";
    }

}
