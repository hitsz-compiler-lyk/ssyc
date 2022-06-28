package top.origami404.ssyc.backend.arm;

import java.util.HashMap;

import top.origami404.ssyc.backend.operand.Operand;

public class ArmInstBinary extends ArmInst {
    private Operand dst, lhs, rhs;

    private static final HashMap<ArmInstKind, String> binaryMap = new HashMap<ArmInstKind, String>() {
        {
            put(ArmInstKind.IAdd, "add");
            put(ArmInstKind.ISub, "sub");
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

    @Override
    public String toString() {
        String op = binaryMap.get(getInst());
        assert op != null;
        String ret = "\t" + op + "\t" + dst.toString() + ",\t" + lhs.toString() + ",\t" + rhs.toString() + "\n";
        return ret;
    }
}
