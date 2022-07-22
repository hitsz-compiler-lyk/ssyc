package top.origami404.ssyc.backend.arm;

import top.origami404.ssyc.backend.operand.IImm;
import top.origami404.ssyc.backend.operand.Operand;

// Store 都是 RegUse
// 0: src     RegUse
// 1: addr    RegUse
// 2: offset  RegUse
public class ArmInstStroe extends ArmInst {

    public ArmInstStroe(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstStroe(ArmBlock block, Operand src, Operand addr) {
        super(ArmInstKind.STORE);
        block.asElementView().add(this);
        this.initOperands(src, addr, new IImm(0));
    }

    public ArmInstStroe(ArmBlock block, Operand src, Operand addr, ArmCondType cond) {
        super(ArmInstKind.STORE);
        block.asElementView().add(this);
        this.setCond(cond);
        this.initOperands(src, addr, new IImm(0));
    }

    public ArmInstStroe(ArmBlock block, Operand src, Operand addr, Operand offset) {
        super(ArmInstKind.STORE);
        block.asElementView().add(this);
        this.initOperands(src, addr, offset);
    }

    public ArmInstStroe(ArmBlock block, Operand src, Operand addr, Operand offset, ArmCondType cond) {
        super(ArmInstKind.STORE);
        block.asElementView().add(this);
        this.setCond(cond);
        this.initOperands(src, addr, offset);
    }

    public ArmInstStroe(Operand src, Operand addr) {
        super(ArmInstKind.STORE);
        this.initOperands(src, addr, new IImm(0));
    }

    public ArmInstStroe(Operand src, Operand addr, ArmCondType cond) {
        super(ArmInstKind.STORE);
        this.setCond(cond);
        this.initOperands(src, addr, new IImm(0));
    }

    public ArmInstStroe(Operand src, Operand addr, Operand offset) {
        super(ArmInstKind.STORE);
        this.initOperands(src, addr, offset);
    }

    public ArmInstStroe(Operand src, Operand addr, Operand offset, ArmCondType cond) {
        super(ArmInstKind.STORE);
        this.setCond(cond);
        this.initOperands(src, addr, offset);
    }

    public Operand getSrc() {
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
        var src = getSrc();
        var addr = getAddr();
        var offset = getOffset();

        var isVector = "";
        if (src.IsFloat()) {
            isVector = "v";
        }
        if (addr.IsAddr() || offset.equals(new IImm(0))) {
            return "\t" + isVector + "str" + getCond() + "\t" + src + ",\t" + addr + "\n";
        } else {
            return "\t" + isVector + "str" + getCond() + "\t" + src + ",\t[" + addr + ",\t" + offset + "]\n";
        }
    }
}
