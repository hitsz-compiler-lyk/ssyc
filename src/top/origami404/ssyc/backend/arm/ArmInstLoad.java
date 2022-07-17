package top.origami404.ssyc.backend.arm;

import top.origami404.ssyc.backend.operand.IImm;
import top.origami404.ssyc.backend.operand.Operand;

// 0: dst     RegDef
// 1: addr    RegUse
// 2: offset  RegUse
public class ArmInstLoad extends ArmInst {

    public ArmInstLoad(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstLoad(ArmBlock block, Operand dst, Operand addr) {
        super(ArmInstKind.LOAD);
        block.asElementView().add(this);
        this.initOperands(dst, addr, new IImm(0));
    }

    public ArmInstLoad(ArmBlock block, Operand dst, Operand addr, ArmCondType cond) {
        super(ArmInstKind.LOAD);
        block.asElementView().add(this);
        this.setCond(cond);
        this.initOperands(dst, addr, new IImm(0));
    }

    public ArmInstLoad(Operand dst, Operand addr) {
        super(ArmInstKind.LOAD);
        this.initOperands(dst, addr, new IImm(0));
    }

    public ArmInstLoad(Operand dst, Operand addr, ArmCondType cond) {
        super(ArmInstKind.LOAD);
        this.setCond(cond);
        this.initOperands(dst, addr, new IImm(0));
    }

    public ArmInstLoad(ArmBlock block, Operand dst, Operand addr, Operand offset) {
        super(ArmInstKind.LOAD);
        block.asElementView().add(this);
        this.initOperands(dst, addr, offset);
    }

    public ArmInstLoad(ArmBlock block, Operand dst, Operand addr, Operand offset, ArmCondType cond) {
        super(ArmInstKind.LOAD);
        block.asElementView().add(this);
        this.setCond(cond);
        this.initOperands(dst, addr, offset);
    }

    public ArmInstLoad(Operand dst, Operand addr, Operand offset) {
        super(ArmInstKind.LOAD);
        this.initOperands(dst, addr, offset);
    }

    public ArmInstLoad(Operand dst, Operand addr, Operand offset, ArmCondType cond) {
        super(ArmInstKind.LOAD);
        this.setCond(cond);
        this.initOperands(dst, addr, offset);
    }

    public Operand getDst() {
        return this.getOperand(0);
    }

    public Operand getAddr() {
        return this.getOperand(1);
    }

    public Operand getOffset() {
        return this.getOperand(2);
    }

    @Override
    public String toString() {
        var dst = getDst();
        var addr = getAddr();
        var offset = getOffset();

        var isVector = "";
        if (dst.IsFloat()) {
            isVector = "v";
        }

        if (addr.IsAddr()) {
            return "\t" + isVector + "ldr" + getCond().toString() + "\t" + dst.toString() + ",\t" + addr.toString()
                    + "\n";
        } else {
            return "\t" + isVector + "ldr" + getCond().toString() + "\t" + dst.toString() + ",\t[" + addr.toString()
                    + ",\t" + offset.toString() + "]\n";
        }
    }

}
