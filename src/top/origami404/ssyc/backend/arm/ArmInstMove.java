package top.origami404.ssyc.backend.arm;

import top.origami404.ssyc.backend.codegen.CodeGenManager;
import top.origami404.ssyc.backend.operand.FImm;
import top.origami404.ssyc.backend.operand.IImm;
import top.origami404.ssyc.backend.operand.Operand;

// 0: dst RegUse
// 1: drc RegUse
public class ArmInstMove extends ArmInst {

    public ArmInstMove(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstMove(ArmBlock block, Operand dst, Operand src) {
        super(ArmInstKind.MOV);
        block.asElementView().add(this);
        this.initOperands(dst, src);
    }

    public ArmInstMove(Operand dst, Operand src) {
        super(ArmInstKind.MOV);
        this.initOperands(dst, src);
    }

    public ArmInstMove(ArmBlock block, Operand dst, Operand src, ArmCondType cond) {
        super(ArmInstKind.MOV);
        block.asElementView().add(this);
        this.setCond(cond);
        this.initOperands(dst, src);
    }

    public ArmInstMove(Operand dst, Operand src, ArmCondType cond) {
        super(ArmInstKind.MOV);
        this.setCond(cond);
        this.initOperands(dst, src);
    }

    public Operand getDst() {
        return this.getOperand(0);
    }

    public Operand getSrc() {
        return this.getOperand(1);
    }

    @Override
    public String toString() {
        var dst = getDst();
        var src = getSrc();

        var isVector = "";
        if (dst.IsFloat() || src.IsFloat()) {
            isVector = "v";
        }

        if (src.IsImm()) {
            int imm = 0;
            if (src.IsIImm()) {
                imm = ((IImm) src).getImm();
            } else {
                imm = Float.floatToIntBits(((FImm) src).getImm());
            }
            // https://developer.arm.com/documentation/dui0473/j/writing-arm-assembly-language/load-immediate-values-using-mov-and-mvn?lang=en
            if (CodeGenManager.checkEncodeImm(~imm)) {
                return "\t" + isVector + "mvn" + getCond() + "\t" + dst + ",\t" + "#" + ~imm + "\n";
            } else if (CodeGenManager.checkEncodeImm(imm)) {
                return "\t" + isVector + "mov" + getCond() + "\t" + dst + ",\t" + "#" + imm + "\n";
            } else if (src.IsFImm()) {
                // https://developer.arm.com/documentation/dui0473/j/writing-arm-assembly-language/load-32-bit-immediate-values-to-a-register-using-ldr-rd---const?lang=en
                // VLDR Rn =Const
                return "\t" + "vldr" + getCond() + "\t" + dst + ",\t" + "=" + ((FImm) src).toHexString() + "\n";
            } else {
                // MOVW 把 16 位立即数放到寄存器的底16位，高16位清0
                // MOVT 把 16 位立即数放到寄存器的高16位，低16位不影响
                var high = imm >>> 16;
                var low = (imm << 16) >>> 16;
                String ret = "";
                ret += "\t" + isVector + "movw" + getCond() + "\t" + dst + ",\t" + "#" + low + "\n";
                if (high != 0) {
                    ret += "\t" + isVector + "movt" + getCond() + "\t" + dst + ",\t" + "#" + high + "\n";
                }
                return ret;
            }
        } else {
            return "\t" + isVector + "mov" + getCond() + "\t" + dst + ",\t" + src + "\n";
        }
    }
}
