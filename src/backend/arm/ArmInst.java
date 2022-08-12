package backend.arm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import backend.operand.Operand;
import backend.operand.Reg;
import utils.INode;
import utils.INodeOwner;
import utils.Log;

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

        Load, StackLoad, ParamLoad,
        Store, StackStore,

        StackAddr,

        Branch,
        Cmp,

        Ltorg,
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

            // ArmInstCall
            put(ArmInstKind.Return, 0);

            // ArmInstCall
            // 引用的参数r0-r3和lr都是被定值的
            put(ArmInstKind.Call, Integer.MAX_VALUE);

            // ArmInstLoad
            put(ArmInstKind.Load, 1);

            // ArmInstStackLoad
            put(ArmInstKind.StackLoad, 1);

            // ArmInstParamLoad
            put(ArmInstKind.ParamLoad, 1);

            // ArmInstStore
            put(ArmInstKind.Store, 0);

            // ArmInstStackStore
            put(ArmInstKind.StackStore, 0);

            // ArmInstStackAddr
            put(ArmInstKind.StackAddr, 1);

            // ArmInstBranch ArmInstCmp
            for (var kind : Arrays.asList(ArmInstKind.Branch, ArmInstKind.Cmp)) {
                put(kind, 0);
            }

            // ArmInstLtorg
            put(ArmInstKind.Ltorg, 0);
        }
    };

    public enum ArmCondType {
        Any,
        Ge, Gt, Eq, Ne, Le, Lt;

        @Override
        public String toString() {
            return this == ArmCondType.Any ? "" : super.toString().toLowerCase();
        }

        public ArmCondType getOppCondType() {
            return switch (this) {
                case Le -> Ge;
                case Ge -> Le;
                case Gt -> Lt;
                case Lt -> Gt;
                case Eq -> Ne;
                case Ne -> Eq;
                default -> this;
            };
        }

        public ArmCondType getEqualOppCondType() {
            return switch (this) {
                case Le -> Ge;
                case Ge -> Le;
                case Gt -> Lt;
                case Lt -> Gt;
                default -> this;
            };
        }
    }

    private ArmInstKind inst;
    private INode<ArmInst, ArmBlock> inode;
    private Set<Reg> regUse, regDef;
    private List<Operand> operands;
    private ArmCondType cond;
    private int printCnt;
    private String symbol;

    public ArmInst(ArmInstKind inst) {
        this.inst = inst;
        this.inode = new INode<>(this);
        this.regUse = new HashSet<>();
        this.regDef = new HashSet<>();
        this.operands = new ArrayList<>();
        this.cond = ArmCondType.Any;
        this.printCnt = 0;
    }

    protected void setPrintCnt(int printCnt) {
        this.printCnt = printCnt;
    }

    public int getPrintCnt() {
        Log.ensure(printCnt != 0, "print cnt == 0");
        return printCnt;
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
        for (int i = 0; i < defCnt; i++) {
            if (operands.get(i).equals(oldOp)) {
                this.replaceOperand(i, op);
            }
        }
    }

    public void replaceUseOperand(Operand oldOp, Operand op) {
        var defCnt = defCntMap.get(inst);
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

    public boolean needLtorg() {
        return (inst.equals(ArmInstKind.MOV) && getOperand(1).IsFImm());
        // || inst.equals(ArmInstKind.Branch)
        // || inst.equals(ArmInstKind.Call);
    }

    public boolean haveLtorg() {
        return (inst.equals(ArmInstKind.Branch) && getCond().equals(ArmCondType.Any))
                || (inst.equals(ArmInstKind.Return))
                || (inst.equals(ArmInstKind.Ltorg));
    }

    public abstract String print();

    public void InitSymbol() {
        var sb = new StringBuffer("@" + print());
        int p = sb.indexOf("\n");
        while (p != sb.length() - 1 && p != -1) {
            sb.insert(p + 1, "@");
            p = sb.indexOf("\n", p + 1);
        }
        symbol = sb.toString();
    }

    public String getSymbol() {
        return symbol;
    }

    public void addSymbol(String symbol) {
        this.symbol += symbol;
    }

    @Override
    public INode<ArmInst, ArmBlock> getINode() {
        return inode;
    }
}
