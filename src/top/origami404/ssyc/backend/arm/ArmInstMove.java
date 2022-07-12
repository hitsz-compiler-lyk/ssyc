package top.origami404.ssyc.backend.arm;

import top.origami404.ssyc.backend.codegen.CodeGenManager;
import top.origami404.ssyc.backend.operand.IImm;
import top.origami404.ssyc.backend.operand.Operand;

public class ArmInstMove extends ArmInst {
    private Operand dst, src;
    private ArmCondType cond;

    public ArmInstMove(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstMove(ArmBlock block, Operand dst, Operand src) {
        super(ArmInstKind.MOV);
        this.dst = dst;
        this.src = src;
        this.cond = ArmCondType.Any;
        block.asElementView().add(this);
    }

    public ArmInstMove(Operand dst, Operand src) {
        super(ArmInstKind.MOV);
        this.dst = dst;
        this.src = src;
        this.cond = ArmCondType.Any;
    }

    public ArmInstMove(ArmBlock block, Operand dst, Operand src, ArmCondType cond) {
        super(ArmInstKind.MOV);
        this.dst = dst;
        this.src = src;
        this.cond = cond;
        block.asElementView().add(this);
    }

    public ArmInstMove(Operand dst, Operand src, ArmCondType cond) {
        super(ArmInstKind.MOV);
        this.dst = dst;
        this.src = src;
        this.cond = cond;
    }

    public void setDst(Operand dst) {
        this.dst = dst;
    }

    public void setSrc(Operand src) {
        this.src = src;
    }

    public void setCond(ArmCondType cond) {
        this.cond = cond;
    }

    @Override
    public String toString() {
        var isVector = "";
        if (dst.IsFloat()) {
            isVector = "v";
        }

        if (src.IsIImm() && CodeGenManager.checkEncodeIImm(~((IImm) src).getImm())) {
            return "\t" + isVector + "mvn" + cond.toString() + "\t" + dst.toString() + ",\t" + "#"
                    + Integer.toString(~((IImm) src).getImm()) + "\n";
        } else if (src.IsIImm() && !CodeGenManager.checkEncodeIImm(((IImm) src).getImm())) {
            var imm = ((IImm) src).getImm();
            var high = imm >>> 16;
            var low = (imm << 16) >>> 16;
            String ret = "";
            ret += "\t" + isVector + "movw" + cond.toString() + "\t" + dst.toString() + ",\t" + "#"
                    + Integer.toString(low) + "\n";
            if (high != 0) {
                ret += "\t" + isVector + "movt" + cond.toString() + "\t" + dst.toString() + ",\t" + "#"
                        + Integer.toString(high)
                        + "\n";
            }
            return ret;
        } else {
            return "\t" + isVector + "mov" + cond.toString() + "\t" + dst.toString() + ",\t" + src.toString() + "\n";
        }
    }
}
