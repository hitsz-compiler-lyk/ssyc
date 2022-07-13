package top.origami404.ssyc.backend.arm;

import top.origami404.ssyc.backend.operand.IImm;
import top.origami404.ssyc.backend.operand.Operand;

public class ArmInstStroe extends ArmInst {
    private Operand src, addr, offset;

    public ArmInstStroe(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstStroe(ArmBlock block, Operand src, Operand addr) {
        super(ArmInstKind.STORE);
        this.src = src;
        this.addr = addr;
        this.offset = new IImm(0);
        block.asElementView().add(this);
    }

    public ArmInstStroe(ArmBlock block, Operand src, Operand addr, ArmCondType cond) {
        super(ArmInstKind.STORE);
        this.src = src;
        this.addr = addr;
        this.offset = new IImm(0);
        block.asElementView().add(this);
        this.setCond(cond);
    }

    public ArmInstStroe(ArmBlock block, Operand src, Operand addr, Operand offset) {
        super(ArmInstKind.STORE);
        this.src = src;
        this.addr = addr;
        this.offset = offset;
        block.asElementView().add(this);
    }

    public ArmInstStroe(ArmBlock block, Operand src, Operand addr, Operand offset, ArmCondType cond) {
        super(ArmInstKind.STORE);
        this.src = src;
        this.addr = addr;
        this.offset = offset;
        block.asElementView().add(this);
        this.setCond(cond);
    }

    @Override
    public String toString() {
        var isVector = "";
        if (src.IsFloat()) {
            isVector = "v";
        }
        if (addr.IsAddr()) {
            return "\t" + isVector + "ldr" + getCond().toString() + "\t" + src.toString() + ",\t" + addr.toString()
                    + "\n";
        } else {
            return "\t" + isVector + "str" + getCond().toString() + "\t" + src.toString() + ",\t[" + addr.toString()
                    + ",\t" + offset.toString() + "]\n";
        }
    }
}
