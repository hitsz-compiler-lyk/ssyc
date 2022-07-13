package top.origami404.ssyc.backend.arm;

import java.util.HashMap;
import java.util.Map;

import top.origami404.ssyc.backend.operand.Operand;
import top.origami404.ssyc.utils.Log;

public class ArmInstBinary extends ArmInst {
    private Operand dst, lhs, rhs;

    private static final Map<ArmInstKind, String> binaryMap = new HashMap<ArmInstKind, String>() {
        {
            put(ArmInstKind.IAdd, "add");
            put(ArmInstKind.ISub, "sub");
            put(ArmInstKind.IRsb, "rsb");
            put(ArmInstKind.IMul, "mul");
            put(ArmInstKind.IDiv, "sdiv");
            put(ArmInstKind.FAdd, "vadd.f32");
            put(ArmInstKind.FSub, "vsub.f32");
            put(ArmInstKind.FMul, "vmul.f32");
            put(ArmInstKind.FDiv, "vdiv.f32");
        }
    };

    public ArmInstBinary(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstBinary(ArmBlock block, ArmInstKind inst, Operand dst, Operand lhs, Operand rhs) {
        super(inst);
        this.dst = dst;
        this.lhs = lhs;
        this.rhs = rhs;
        block.asElementView().add(this);
    }

    public ArmInstBinary(ArmBlock block, ArmInstKind inst, Operand dst, Operand lhs, Operand rhs, ArmCondType cond) {
        super(inst);
        this.dst = dst;
        this.lhs = lhs;
        this.rhs = rhs;
        block.asElementView().add(this);
        this.setCond(cond);
    }

    public ArmInstBinary(ArmInstKind inst, Operand dst, Operand lhs, Operand rhs) {
        super(inst);
        this.dst = dst;
        this.lhs = lhs;
        this.rhs = rhs;
    }

    public ArmInstBinary(ArmInstKind inst, Operand dst, Operand lhs, Operand rhs, ArmCondType cond) {
        super(inst);
        this.dst = dst;
        this.lhs = lhs;
        this.rhs = rhs;
        this.setCond(cond);
    }

    @Override
    public String toString() {
        String op = binaryMap.get(getInst());
        Log.ensure(op != null);
        String ret = "\t" + op + getCond().toString() + "\t" + dst.toString() + ",\t" + lhs.toString() + ",\t"
                + rhs.toString() + "\n";
        return ret;
    }
}
