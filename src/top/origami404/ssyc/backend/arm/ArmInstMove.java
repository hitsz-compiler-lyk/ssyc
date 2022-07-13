package top.origami404.ssyc.backend.arm;

import top.origami404.ssyc.backend.codegen.CodeGenManager;
import top.origami404.ssyc.backend.operand.IImm;
import top.origami404.ssyc.backend.operand.Operand;

public class ArmInstMove extends ArmInst {
    private Operand dst, src;

    public ArmInstMove(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstMove(ArmBlock block, Operand dst, Operand src) {
        super(ArmInstKind.MOV);
        this.dst = dst;
        this.src = src;
        block.asElementView().add(this);
    }

    public ArmInstMove(Operand dst, Operand src) {
        super(ArmInstKind.MOV);
        this.dst = dst;
        this.src = src;
    }

    public ArmInstMove(ArmBlock block, Operand dst, Operand src, ArmCondType cond) {
        super(ArmInstKind.MOV);
        this.dst = dst;
        this.src = src;
        block.asElementView().add(this);
        this.setCond(cond);
    }

    public ArmInstMove(Operand dst, Operand src, ArmCondType cond) {
        super(ArmInstKind.MOV);
        this.dst = dst;
        this.src = src;
        this.setCond(cond);
    }

    public void setDst(Operand dst) {
        this.dst = dst;
    }

    public void setSrc(Operand src) {
        this.src = src;
    }

    @Override
    public String toString() {
        var isVector = "";
        if (dst.IsFloat() || src.IsFloat()) {
            isVector = "v";
        }

        if (src.IsIImm() && CodeGenManager.checkEncodeIImm(~((IImm) src).getImm())) {
            return "\t" + isVector + "mvn" + getCond().toString() + "\t" + dst.toString() + ",\t" + "#"
                    + Integer.toString(~((IImm) src).getImm()) + "\n";
        } else if (src.IsIImm() && !CodeGenManager.checkEncodeIImm(((IImm) src).getImm())) {
            var imm = ((IImm) src).getImm();
            var high = imm >>> 16;
            var low = (imm << 16) >>> 16;
            String ret = "";
            ret += "\t" + isVector + "movw" + getCond().toString() + "\t" + dst.toString() + ",\t" + "#"
                    + Integer.toString(low) + "\n";
            if (high != 0) {
                ret += "\t" + isVector + "movt" + getCond().toString() + "\t" + dst.toString() + ",\t" + "#"
                        + Integer.toString(high)
                        + "\n";
            }
            return ret;
        } else {
            return "\t" + isVector + "mov" + getCond().toString() + "\t" + dst.toString() + ",\t" + src.toString()
                    + "\n";
        }
    }
}
