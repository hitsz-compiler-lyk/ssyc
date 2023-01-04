package backend.arm;

import backend.operand.Operand;
import utils.Log;

import java.util.HashMap;
import java.util.Map;

// mla dst op1 op2 op3: dst = op3 + op1 * op2
// mls dst op1 op2 op3: dst = op3 - op1 * op2
// smmla dst op1 op2 op3: dst = op3 + op1 * op2[63:32]
// smmls dst op1 op2 op3: dst = op3 - op1 * op2[63:32]
// 0: dst RegDef
// 1: op1 RegUse
// 2: op2 RegUse
// 2: op3 RegUse
public class ArmInstTernay extends ArmInst {

    private static final Map<ArmInstKind, String> ternayMap = new HashMap<ArmInstKind, String>() {
        {
            put(ArmInstKind.IMulAdd, "mla");
            put(ArmInstKind.IMulSub, "mls");
            put(ArmInstKind.ILMulAdd, "smmla");
            put(ArmInstKind.ILMulSub, "smmls");
            // put(ArmInstKind.FMulAdd, "vmla.f32"); // 只有三个参数
            // put(ArmInstKind.FMulSub, "vmls.f32"); // 只有三个参数
        }
    };

    public ArmInstTernay(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstTernay(ArmBlock block, ArmInstKind inst, Operand dst, Operand op1, Operand op2, Operand op3) {
        super(inst);
        block.asElementView().add(this);
        this.initOperands(dst, op1, op2, op3);
        this.setPrintCnt(1);
    }

    public ArmInstTernay(ArmBlock block, ArmInstKind inst, Operand dst, Operand op1, Operand op2, Operand op3,
            ArmCondType cond) {
        super(inst);
        block.asElementView().add(this);
        this.setCond(cond);
        this.initOperands(dst, op1, op2, op3);
        this.setPrintCnt(1);
    }

    public ArmInstTernay(ArmInstKind inst, Operand dst, Operand op1, Operand op2, Operand op3) {
        super(inst);
        this.initOperands(dst, op1, op2, op3);
        this.setPrintCnt(1);
    }

    public ArmInstTernay(ArmInstKind inst, Operand dst, Operand op1, Operand op2, Operand op3, ArmCondType cond) {
        super(inst);
        this.setCond(cond);
        this.initOperands(dst, op1, op2, op3);
        this.setPrintCnt(1);
    }

    public Operand getDst() {
        return this.getOperand(0);
    }

    public Operand getOp1() {
        return this.getOperand(1);
    }

    public Operand getOp2() {
        return this.getOperand(2);
    }

    public Operand getOp3() {
        return this.getOperand(3);
    }

    @Override
    public String print() {
        String op = ternayMap.get(getInst());
        Log.ensure(op != null);
        String ret = "\t" + op + getCond().toString() + "\t" + getDst().print() + ",\t" + getOp1().print() + ",\t"
                + getOp2().print() + ",\t" + getOp3().print() + "\n";
        return ret;
    }

}
