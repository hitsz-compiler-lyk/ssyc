package backend.lir.inst;

import backend.lir.ArmBlock;
import backend.lir.operand.Operand;

// mla dst op1 op2 op3: dst = op3 + op1 * op2
// mls dst op1 op2 op3: dst = op3 - op1 * op2
// smmla dst op1 op2 op3: dst = op3 + op1 * op2[63:32]
// smmls dst op1 op2 op3: dst = op3 - op1 * op2[63:32]
// 0: dst RegDef
// 1: op1 RegUse
// 2: op2 RegUse
// 2: op3 RegUse
public class ArmInstTernary extends ArmInst {
    public ArmInstTernary(ArmBlock block, ArmInstKind inst, Operand dst, Operand op1, Operand op2, Operand op3) {
        this(inst, dst, op1, op2, op3, ArmCondType.Any);
        block.add(this);
    }

    public ArmInstTernary(ArmInstKind inst, Operand dst, Operand op1, Operand op2, Operand op3, ArmCondType cond) {
        super(inst);
        this.setCond(cond);
        this.initOperands(dst, op1, op2, op3);
    }

    public Operand getDst() { return getOperand(0); }
    public Operand getOp1() { return getOperand(1); }
    public Operand getOp2() { return getOperand(2); }
    public Operand getOp3() { return getOperand(3); }
}
