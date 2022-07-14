package top.origami404.ssyc.backend.arm;

import top.origami404.ssyc.backend.operand.IImm;
import top.origami404.ssyc.backend.operand.Operand;

public class ArmInstLoad extends ArmInst {
    private Operand dst, addr, offset;

    public ArmInstLoad(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstLoad(ArmBlock block, Operand dst, Operand addr) {
        super(ArmInstKind.LOAD);
        this.dst = dst;
        this.addr = addr;
        this.offset = new IImm(0);
        block.asElementView().add(this);
        this.addRegDef(this.dst);
        this.addRegUse(this.addr);
        this.addRegUse(this.offset);
    }

    public ArmInstLoad(ArmBlock block, Operand dst, Operand addr, ArmCondType cond) {
        super(ArmInstKind.LOAD);
        this.dst = dst;
        this.addr = addr;
        this.offset = new IImm(0);
        block.asElementView().add(this);
        this.setCond(cond);
        this.addRegDef(this.dst);
        this.addRegUse(this.addr);
        this.addRegUse(this.offset);
    }

    public ArmInstLoad(ArmBlock block, Operand dst, Operand addr, Operand offset) {
        super(ArmInstKind.LOAD);
        this.dst = dst;
        this.addr = addr;
        this.offset = offset;
        block.asElementView().add(this);
        this.addRegDef(this.dst);
        this.addRegUse(this.addr);
        this.addRegUse(this.offset);
    }

    public ArmInstLoad(ArmBlock block, Operand dst, Operand addr, Operand offset, ArmCondType cond) {
        super(ArmInstKind.LOAD);
        this.dst = dst;
        this.addr = addr;
        this.offset = offset;
        block.asElementView().add(this);
        this.setCond(cond);
        this.addRegDef(this.dst);
        this.addRegUse(this.addr);
        this.addRegUse(this.offset);
    }

    @Override
    public String toString() {
        var isVector = "";
        if (dst.IsFloat()) {
            isVector = "v";
        }

        if (addr.IsAddr()) {
            return "\t" + isVector + "ldr" + getCond().toString() + "\t" + dst.toString() + ",\t" + addr.toString()
                    + "\n";
        } else {
            return "\t" + isVector + "ldr" + getCond().toString() + "\t" + dst.toString() + ",\t[" + addr.toString()
                    + ",\t"
                    + offset.toString() + "]\n";
        }
    }

}
