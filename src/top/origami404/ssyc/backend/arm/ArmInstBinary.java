package top.origami404.ssyc.backend.arm;

import java.util.HashMap;
import java.util.Map;

import top.origami404.ssyc.backend.operand.Operand;
import top.origami404.ssyc.utils.Log;

// 0: dst RegDef
// 1: lhs RegUse
// 2: rhs RegUse
public class ArmInstBinary extends ArmInst {
    boolean isFixOffset = false;
    boolean isStack = false;
    Operand trueOffset;
    ArmInstMove offsetMove;


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
        this.isFixOffset = false;
        this.isStack = false;
    }

    public ArmInstBinary(ArmBlock block, ArmInstKind inst, Operand dst, Operand lhs, Operand rhs, ArmCondType cond) {
        super(inst);
        block.asElementView().add(this);
        this.setCond(cond);
        this.initOperands(dst, lhs, rhs);
        this.setPrintCnt(1);
        this.isFixOffset = false;
        this.isStack = false;
    }

    public ArmInstBinary(ArmInstKind inst, Operand dst, Operand lhs, Operand rhs) {
        super(inst);
        this.initOperands(dst, lhs, rhs);
        this.setPrintCnt(1);
        this.isFixOffset = false;
        this.isStack = false;
    }

    public ArmInstBinary(ArmInstKind inst, Operand dst, Operand lhs, Operand rhs, ArmCondType cond) {
        super(inst);
        this.setCond(cond);
        this.initOperands(dst, lhs, rhs);
        this.setPrintCnt(1);
        this.isFixOffset = false;
        this.isStack = false;
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

    public void replaceRhs(Operand rhs) {
        this.replaceOperand(2, rhs);
    }

    public void setTrueOffset(Operand trueOffset) {
        this.trueOffset = trueOffset;
    }

    public void setFixOffset(boolean isFixOffset) {
        this.isFixOffset = isFixOffset;
    }

    public boolean isFixOffset() {
        return isFixOffset;
    }

    public void setOffsetMove(ArmInstMove offsetMove) {
        this.offsetMove = offsetMove;
    }

    public ArmInstMove getOffsetMove() {
        return offsetMove;
    }

    public void setStack(boolean isStack) {
        this.isStack = isStack;
    }

    public boolean isStack() {
        return isStack;
    }

    @Override
    public String print() {
        String op = binaryMap.get(getInst());
        var dst = getDst();
        var lhs = getLhs();
        var rhs = getRhs();
        if (trueOffset != null) {
            rhs = trueOffset;
        }
        Log.ensure(op != null);
        String ret = "\t" + op + getCond().toString() + "\t" + dst.print() + ",\t" + lhs.print() + ",\t"
                + rhs.print() + "\n";
        return ret;
    }
}
