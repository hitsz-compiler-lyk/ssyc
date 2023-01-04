package backend.arm;

import backend.operand.Operand;
import utils.Log;

import java.util.HashMap;
import java.util.Map;

// 0: dst RegDef
// 1: lhs RegUse
// 2: rhs RegUse
public class ArmInstBinary extends ArmInst {
    private static final Map<ArmInstKind, String> binaryMap = new HashMap<ArmInstKind, String>() {
        {
            put(ArmInstKind.IAdd, "add");
            put(ArmInstKind.ISub, "sub");
            put(ArmInstKind.IRsb, "rsb");
            put(ArmInstKind.IMul, "mul");
            put(ArmInstKind.IDiv, "sdiv");
            put(ArmInstKind.ILMul, "smmul"); // smmul Rd Rm Rs : Rd = (Rm * Rs)[63:32]
            put(ArmInstKind.FAdd, "vadd");
            put(ArmInstKind.FSub, "vsub");
            put(ArmInstKind.FMul, "vmul");
            put(ArmInstKind.FDiv, "vdiv");
            put(ArmInstKind.Bic, "bic");
        }
    };

    ArmShift shift;

    public static boolean isBinary(ArmInstKind kind) {
        return binaryMap.containsKey(kind);
    }

    public ArmInstBinary(ArmInstKind inst) {
        super(inst);
    }

    public ArmInstBinary(ArmBlock block, ArmInstKind inst, Operand dst, Operand lhs, Operand rhs) {
        super(inst);
        block.asElementView().add(this);
        this.initOperands(dst, lhs, rhs);
        this.setPrintCnt(1);
        this.shift = null;
    }

    public ArmInstBinary(ArmBlock block, ArmInstKind inst, Operand dst, Operand lhs, Operand rhs, ArmCondType cond) {
        super(inst);
        block.asElementView().add(this);
        this.setCond(cond);
        this.initOperands(dst, lhs, rhs);
        this.setPrintCnt(1);
        this.shift = null;
    }

    public ArmInstBinary(ArmInstKind inst, Operand dst, Operand lhs, Operand rhs) {
        super(inst);
        this.initOperands(dst, lhs, rhs);
        this.setPrintCnt(1);
        this.shift = null;
    }

    public ArmInstBinary(ArmInstKind inst, Operand dst, Operand lhs, Operand rhs, ArmCondType cond) {
        super(inst);
        this.setCond(cond);
        this.initOperands(dst, lhs, rhs);
        this.setPrintCnt(1);
        this.shift = null;
    }

    public Operand getDst() {
        return this.getOperand(0);
    }

    public Operand getLhs() {
        return this.getOperand(1);
    }

    public Operand getRhs() {
        return this.getOperand(2);
    }

    public void setShift(ArmShift shift) {
        this.shift = shift;
    }

    public ArmShift getShift() {
        return shift;
    }

    @Override
    public String print() {
        String op = binaryMap.get(getInst());
        var dst = getDst();
        var lhs = getLhs();
        var rhs = getRhs();
        String fprint = dst.IsFloat() ? ".f32" : "";
        Log.ensure(op != null);
        if (shift != null) {
            return "\t" + op + getCond().toString() + fprint + "\t" + dst.print() + ",\t" + lhs.print() + ",\t"
                    + rhs.print() + shift.toString() + "\n";
        } else {
            return "\t" + op + getCond().toString() + fprint + "\t" + dst.print() + ",\t" + lhs.print() + ",\t"
                    + rhs.print() + "\n";
        }
    }
}
