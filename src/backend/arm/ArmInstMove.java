package backend.arm;

import backend.codegen.CodeGenManager;
import backend.operand.FImm;
import backend.operand.IImm;
import backend.operand.Operand;
import utils.Log;

// 0: dst RegUse
// 1: drc RegUse
public class ArmInstMove extends ArmInst {
    ArmShift shift;

    public ArmInstMove(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstMove(ArmBlock block, Operand dst, Operand src) {
        super(ArmInstKind.MOV);
        block.asElementView().add(this);
        this.initOperands(dst, src);
        if (src.IsIImm()) {
            int imm = ((IImm) src).getImm();
            if (CodeGenManager.checkEncodeImm(~imm) || CodeGenManager.checkEncodeImm(imm)) {
                this.setPrintCnt(1);
            } else {
                this.setPrintCnt(2);
            }
        } else if (src.IsAddr()) {
            this.setPrintCnt(2);
        } else {
            this.setPrintCnt(1);
        }
        this.shift = null;
    }

    public ArmInstMove(Operand dst, Operand src) {
        super(ArmInstKind.MOV);
        this.initOperands(dst, src);
        if (src.IsIImm()) {
            int imm = ((IImm) src).getImm();
            if (CodeGenManager.checkEncodeImm(~imm) || CodeGenManager.checkEncodeImm(imm)) {
                this.setPrintCnt(1);
            } else {
                this.setPrintCnt(2);
            }
        } else if (src.IsAddr()) {
            this.setPrintCnt(2);
        } else {
            this.setPrintCnt(1);
        }
        this.shift = null;
    }

    public ArmInstMove(ArmBlock block, Operand dst, Operand src, ArmCondType cond) {
        super(ArmInstKind.MOV);
        block.asElementView().add(this);
        this.setCond(cond);
        this.initOperands(dst, src);
        if (src.IsIImm()) {
            int imm = ((IImm) src).getImm();
            if (CodeGenManager.checkEncodeImm(~imm) || CodeGenManager.checkEncodeImm(imm)) {
                this.setPrintCnt(1);
            } else {
                this.setPrintCnt(2);
            }
        } else if (src.IsAddr()) {
            this.setPrintCnt(2);
        } else {
            this.setPrintCnt(1);
        }
        this.shift = null;
    }

    public ArmInstMove(Operand dst, Operand src, ArmCondType cond) {
        super(ArmInstKind.MOV);
        this.setCond(cond);
        this.initOperands(dst, src);
        if (src.IsIImm()) {
            int imm = ((IImm) src).getImm();
            if (CodeGenManager.checkEncodeImm(~imm) || CodeGenManager.checkEncodeImm(imm)) {
                this.setPrintCnt(1);
            } else {
                this.setPrintCnt(2);
            }
        } else if (src.IsAddr()) {
            this.setPrintCnt(2);
        } else {
            this.setPrintCnt(1);
        }
        this.shift = null;
    }

    public Operand getDst() {
        return this.getOperand(0);
    }

    public Operand getSrc() {
        return this.getOperand(1);
    }

    public void setShift(ArmShift shift) {
        this.shift = shift;
    }

    public ArmShift getShift() {
        return shift;
    }

    @Override
    public String print() {
        var dst = getDst();
        var src = getSrc();

        var isVector = "";
        if (dst.IsFloat() || src.IsFloat()) {
            isVector = "v";
        }

        if (src.IsIImm()) {
            int imm = ((IImm) src).getImm();
            // https://developer.arm.com/documentation/dui0473/j/writing-arm-assembly-language/load-immediate-values-using-mov-and-mvn?lang=en
            if (CodeGenManager.checkEncodeImm(~imm)) {
                return "\t" + isVector + "mvn" + getCond().toString() + "\t" + dst.print() + ",\t" + "#"
                        + Integer.toString(~imm) + "\n";
            } else if (CodeGenManager.checkEncodeImm(imm)) {
                return "\t" + isVector + "mov" + getCond().toString() + "\t" + dst.print() + ",\t" + "#"
                        + Integer.toString(imm) + "\n";
            } else {
                // MOVW ??? 16 ?????????????????????????????????16?????????16??????0
                // MOVT ??? 16 ?????????????????????????????????16?????????16????????????
                var high = imm >>> 16;
                var low = (imm << 16) >>> 16;
                String ret = "";
                ret += "\t" + isVector + "movw" + getCond().toString() + "\t" + dst.print() + ",\t" + "#"
                        + Integer.toString(low) + "\n";
                if (high != 0) {
                    ret += "\t" + isVector + "movt" + getCond().toString() + "\t" + dst.print() + ",\t" + "#"
                            + Integer.toString(high) + "\n";
                }
                return ret;
            }
        } else if (src.IsFImm()) {
            // https://developer.arm.com/documentation/dui0473/j/writing-arm-assembly-language/load-32-bit-immediate-values-to-a-register-using-ldr-rd---const?lang=en
            // VLDR Rn =Const
            return "\t" + "vldr" + getCond().toString() + "\t" + dst.print() + ",\t" + "="
                    + ((FImm) src).toHexString() + "\n";
        } else if (src.IsAddr()) {
            Log.ensure(!dst.IsFloat(), "load addr into vfp");
            // return "\tldr" + getCond().toString() + "\t" + dst.print() + ",\t=" +
            // src.print() + "\n";
            return "\tmovw" + getCond().toString() + "\t" + dst.print() + ",\t:lower16:" + src.print() + "\n" +
                    "\tmovt" + getCond().toString() + "\t" + dst.print() + ",\t:upper16:" + src.print() + "\n";
        } else if (shift != null) {
            return "\t" + isVector + "mov" + getCond().toString() + "\t" + dst.print() + ",\t" + src.print()
                    + shift.toString() + "\n";
        } else {
            return "\t" + isVector + "mov" + getCond().toString() + "\t" + dst.print() + ",\t" + src.print() + "\n";
        }
    }
}
