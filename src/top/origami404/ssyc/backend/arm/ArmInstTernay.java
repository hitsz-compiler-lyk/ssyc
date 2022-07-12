package top.origami404.ssyc.backend.arm;

import java.util.HashMap;
import java.util.Map;

import top.origami404.ssyc.backend.operand.Operand;
import top.origami404.ssyc.utils.Log;

public class ArmInstTernay extends ArmInst {
    private Operand dst, op1, op2, op3;

    private static final Map<ArmInstKind, String> ternayMap = new HashMap<ArmInstKind, String>() {
        {
            put(ArmInstKind.IMulAdd, "mla");
            put(ArmInstKind.IMulSub, "mls");
            put(ArmInstKind.FMulAdd, "vmla.f32");
            put(ArmInstKind.FMulSub, "vmls.f32");
        }
    };

    public ArmInstTernay(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstTernay(ArmBlock block, ArmInstKind inst, Operand dst, Operand op1, Operand op2, Operand op3) {
        super(inst);
        this.dst = dst;
        this.op1 = op1;
        this.op2 = op2;
        this.op3 = op3;
        block.asElementView().add(this);
    }

    @Override
    public String toString() {
        String op = ternayMap.get(getInst());
        Log.ensure(op != null);
        String ret = "\t" + op + "\t" + dst.toString() + ",\t" + op1.toString() + ",\t" + op2.toString() + ",\t"
                + op3.toString() + "\n";
        return ret;
    }

}
