package top.origami404.ssyc.backend.arm;

import top.origami404.ssyc.backend.operand.IImm;
import top.origami404.ssyc.backend.operand.Operand;

public class ArmInstLoad extends ArmInst {
    private Operand dst, addr, offset;

    public ArmInstLoad(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstLoad(ArmBlock block, Operand dst, Operand addr) {
        super(ArmInstKind.MOV);
        this.dst = dst;
        this.addr = addr;
        this.offset = new IImm(0);
        block.asElementView().add(this);
    }

    public ArmInstLoad(ArmBlock block, Operand dst, Operand addr, Operand offset) {
        super(ArmInstKind.MOV);
        this.dst = dst;
        this.addr = addr;
        this.offset = offset;
        block.asElementView().add(this);
    }

    @Override
    public String toString() {
        if (addr.getLabel() != "") {
            return "\t" + "ldr" + "\t" + dst.toString() + ",\t" + addr.toString() + " \n";
        } else {
            return "\t" + "ldr" + "\t" + dst.toString() + ",\t[" + addr.toString() + ",\t" + offset.toString() + "]\n";
        }
    }

}
