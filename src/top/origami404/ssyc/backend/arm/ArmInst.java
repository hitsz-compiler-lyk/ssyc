package top.origami404.ssyc.backend.arm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import top.origami404.ssyc.backend.operand.IPhyReg;
import top.origami404.ssyc.backend.operand.Operand;
import top.origami404.ssyc.backend.operand.Reg;
import top.origami404.ssyc.utils.INode;
import top.origami404.ssyc.utils.INodeOwner;

public abstract class ArmInst implements INodeOwner<ArmInst, ArmBlock> {

    public enum ArmInstKind {
        IAdd, ISub, IRsb, IMul, IDiv,
        FAdd, FSub, FMul, FDiv,

        IMulAdd, IMulSub,
        FMulAdd, FMulSub,

        INeg, FNeg,

        IntToFloat,
        FloatToInt,

        MOV,

        Call,
        Return,

        LOAD,
        STORE,

        Branch,
        Cmp,
    }

    // RegDef [0 ,cnt)
    private static final Map<ArmInstKind, Integer> defCntMap = new HashMap<ArmInstKind, Integer>() {
        {
            // ArmInstBinary
            for (var kind : Arrays.asList(ArmInstKind.IAdd, ArmInstKind.ISub, ArmInstKind.IRsb, ArmInstKind.IMul,
                    ArmInstKind.IDiv, ArmInstKind.FAdd, ArmInstKind.FSub, ArmInstKind.FMul, ArmInstKind.FDiv)) {
                put(kind, 1);
            }

            // ArmInstTernay
            for (var kind : Arrays.asList(ArmInstKind.IMulAdd, ArmInstKind.IMulSub, ArmInstKind.FMulAdd,
                    ArmInstKind.FMulSub)) {
                put(kind, 1);
            }

            // ArmInstUnary
            for (var kind : Arrays.asList(ArmInstKind.INeg, ArmInstKind.FNeg)) {
                put(kind, 1);
            }

            // ArmInstIntToFloat ArmInstFloatToInt
            for (var kind : Arrays.asList(ArmInstKind.IntToFloat, ArmInstKind.FloatToInt)) {
                put(kind, 1);
            }

            // ArmInstMove
            put(ArmInstKind.MOV, 1);

            // ArmInstCall ArmInstReturn
            for (var kind : Arrays.asList(ArmInstKind.Call, ArmInstKind.Return)) {
                put(kind, 0);
            }

            // ArmInstLoad
            put(ArmInstKind.LOAD, 1);

            // ArmInstStore
            put(ArmInstKind.STORE, 0);

            // ArmInstBranch ArmInstCmp
            for (var kind : Arrays.asList(ArmInstKind.Branch, ArmInstKind.Cmp)) {
                put(kind, 0);
            }
        }
    };

    public enum ArmCondType {
        Any,
        Ge, Gt, Eq, Ne, Le, Lt;

        @Override
        public String toString() {
            switch (this) {
                case Any:
                    return "";
                default:
                    return super.toString().toLowerCase();
            }
        }

        public ArmCondType getOppCondType() {
            switch (this) {
                case Le:
                    return Ge;
                case Ge:
                    return Le;
                case Gt:
                    return Lt;
                case Lt:
                    return Gt;
                default:
                    return this;
            }
        }
    }

    private ArmInstKind inst;
    private INode<ArmInst, ArmBlock> inode;
    private Set<Reg> regUse, regDef;
    private List<Operand> operands;
    private ArmCondType cond;
    private int defCnt;

    public ArmInst(ArmInstKind inst) {
        this.inst = inst;
        this.inode = new INode<>(this);
        this.regUse = new HashSet<Reg>();
        this.regDef = new HashSet<Reg>();
        this.operands = new ArrayList<Operand>();
        this.cond = ArmCondType.Any;
    }

    public ArmInstKind getInst() {
        return inst;
    }

    public Set<Reg> getRegUse() {
        return regUse;
    }

    public Set<Reg> getRegDef() {
        return regDef;
    }

    public int getDefCnt() {
        return defCnt;
    }

    public void setDefCnt(int defCnt) {
        this.defCnt = defCnt;
    }

    public void addRegUse(Operand r) {
        if (r instanceof Reg) {
            regUse.add((Reg) r);
        }
    }

    public void addRegDef(Operand r) {
        if (r instanceof Reg) {
            regDef.add((Reg) r);
        }
    }

    public void delRegUse(Operand r) {
        if (r instanceof Reg) {
            regUse.remove((Reg) r);
        }
    }

    public void delRegDef(Operand r) {
        if (r instanceof Reg) {
            regDef.remove((Reg) r);
        }
    }

    public List<Operand> getOperands() {
        return operands;
    }

    public Operand getOperand(int index) {
        return operands.get(index);
    }

    public void initOperands(Operand... op) {
        var defCnt = defCntMap.get(inst);
        if (inst.equals(ArmInstKind.Call)) {
            defCnt = this.defCnt;
        }
        for (int i = 0; i < op.length; i++) {
            operands.add(op[i]);
            if (i < defCnt) {
                this.addRegDef(op[i]);
            } else {
                this.addRegUse(op[i]);
            }
            if (op[i] instanceof Reg) {
                ((Reg) op[i]).addInst(this);
            }
        }
    }

    public void replaceOperand(int idx, Operand op) {
        var defCnt = defCntMap.get(inst);
        if (inst.equals(ArmInstKind.Call)) {
            defCnt = this.defCnt;
        }
        var oldOp = operands.get(idx);
        if (oldOp instanceof Reg) {
            ((Reg) oldOp).removeInst(this);
        }
        if (op instanceof Reg) {
            ((Reg) op).addInst(this);
        }
        if (idx < defCnt) {
            this.delRegDef(oldOp);
            this.addRegDef(op);
        } else {
            this.delRegUse(oldOp);
            this.addRegUse(op);
        }
        operands.set(idx, op);
    }

    public void replaceOperand(Operand oldOp, Operand op) {
        for (int i = 0; i < operands.size(); i++) {
            if (operands.get(i).equals(oldOp)) {
                this.replaceOperand(i, op);
            }
        }
    }

    public void replaceDefOperand(Operand oldOp, Operand op) {
        var defCnt = defCntMap.get(inst);
        if (inst.equals(ArmInstKind.Call)) {
            defCnt = this.defCnt;
        }
        for (int i = 0; i < defCnt; i++) {
            if (operands.get(i).equals(oldOp)) {
                this.replaceOperand(i, op);
            }
        }
    }

    public void replaceUseOperand(Operand oldOp, Operand op) {
        var defCnt = defCntMap.get(inst);
        if (inst.equals(ArmInstKind.Call)) {
            defCnt = this.defCnt;
        }
        for (int i = defCnt; i < operands.size(); i++) {
            if (operands.get(i).equals(oldOp)) {
                this.replaceOperand(i, op);
            }
        }
    }

    public ArmCondType getCond() {
        return cond;
    }

    public void setCond(ArmCondType cond) {
        this.cond = cond;
    }

    public boolean isStackLoad() {
        return inst.equals(ArmInstKind.LOAD) && getOperand(1).equals(new IPhyReg("sp"));
    }

    public boolean isStackStore() {
        return inst.equals(ArmInstKind.STORE) && getOperand(1).equals(new IPhyReg("sp"));
    }

    public abstract String print();

    @Override
    public INode<ArmInst, ArmBlock> getINode() {
        return inode;
    }
}
