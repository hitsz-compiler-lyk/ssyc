package backend.lir.inst;

import backend.lir.ArmBlock;
import backend.lir.operand.FImm;
import backend.lir.operand.Operand;
import backend.lir.operand.Reg;
import utils.INode;
import utils.INodeOwner;
import utils.Log;

import java.util.*;

public abstract class ArmInst implements INodeOwner<ArmInst, ArmBlock> {

    public enum ArmInstKind {
        IAdd, ISub, IRsb, IMul, IDiv, ILMul,
        FAdd, FSub, FMul, FDiv,
        Bic,

        IMulAdd, IMulSub, ILMulAdd, ILMulSub,
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
                    ArmInstKind.ILMul,
                    ArmInstKind.IDiv, ArmInstKind.FAdd, ArmInstKind.FSub, ArmInstKind.FMul, ArmInstKind.FDiv,
                    ArmInstKind.Bic)) {
                put(kind, 1);
            }

            // ArmInstTernay
            for (var kind : Arrays.asList(ArmInstKind.IMulAdd, ArmInstKind.IMulSub, ArmInstKind.ILMulAdd,
                    ArmInstKind.ILMulSub, ArmInstKind.FMulAdd, ArmInstKind.FMulSub)) {
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
                case Le -> Gt;
                case Ge -> Lt;
                case Gt -> Le;
                case Lt -> Ge;
                case Eq -> Ne;
                case Ne -> Eq;
                default -> throw new RuntimeException("Unknown cond: " + this);
            };
        }

        public ArmCondType getEqualOppCondType() {
            return switch (this) {
                case Le -> Ge;
                case Ge -> Le;
                case Gt -> Lt;
                case Lt -> Gt;
                default -> throw new RuntimeException("Unknown cond: " + this);
            };
        }
    }

    private final ArmInstKind kind;

    private final Set<Reg> regUse;
    private final Set<Reg> regDef;
    private final List<Operand> operands;

    private ArmCondType cond;

    private int printCnt;
    private String symbol;

    private final INode<ArmInst, ArmBlock> inode;

    protected ArmInst(ArmInstKind kind) {
        this.kind = kind;
        this.inode = new INode<>(this);
        this.regUse = new LinkedHashSet<>();
        this.regDef = new LinkedHashSet<>();
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

    public ArmInstKind getKind() {
        return kind;
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
        var defCnt = defCntMap.get(kind);
        for (int i = 0; i < op.length; i++) {
            operands.add(op[i]);
            if (i < defCnt) {
                this.addRegDef(op[i]);
            } else {
                this.addRegUse(op[i]);
            }
        }
    }

    // WIP: spill init for def/use
    protected void initDefOperands(Operand... operands) {
        for (final var op : operands) {
            initOperand(regDef, op);
        }
    }

    protected void initUseOperands(Operand... operands) {
        for (final var op : operands) {
            initOperand(regUse, op);
        }
    }

    private void initOperand(Set<Reg> useOrDef, Operand op) {
        operands.add(op);
        if (op instanceof Reg reg) {
            useOrDef.add(reg);
        }
    }

    public void replaceOperand(int idx, Operand op) {
        var defCnt = defCntMap.get(kind);
        var oldOp = operands.get(idx);
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
        var defCnt = defCntMap.get(kind);
        for (int i = 0; i < defCnt; i++) {
            if (operands.get(i).equals(oldOp)) {
                this.replaceOperand(i, op);
            }
        }
    }

    public void replaceUseOperand(Operand oldOp, Operand op) {
        var defCnt = defCntMap.get(kind);
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
        return (kind.equals(ArmInstKind.MOV) && getOperand(1) instanceof FImm);
        // || inst.equals(ArmInstKind.Branch)
        // || inst.equals(ArmInstKind.Call);
    }

    public boolean haveLtorg() {
        return (kind.equals(ArmInstKind.Branch) && getCond().equals(ArmCondType.Any))
                || (kind.equals(ArmInstKind.Return) && getCond().equals(ArmCondType.Any))
                || (kind.equals(ArmInstKind.Ltorg));
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
