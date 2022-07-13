package top.origami404.ssyc.backend.arm;

import java.util.ArrayList;
import java.util.List;

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
    }

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
    private List<Reg> regUse, regDef;
    private ArmCondType cond;

    public ArmInst(ArmInstKind inst) {
        this.inst = inst;
        this.inode = new INode<>(this);
        this.regUse = new ArrayList<Reg>();
        this.regDef = new ArrayList<Reg>();
        this.cond = ArmCondType.Any;
    }

    public ArmInstKind getInst() {
        return inst;
    }

    public List<Reg> getRegUse() {
        return regUse;
    }

    public List<Reg> getRegDef() {
        return regDef;
    }

    public void addRegUse(Operand r) {
        if (!r.IsImm()) {
            regUse.add((Reg) r);
        }
    }

    public ArmCondType getCond() {
        return cond;
    }

    public void setCond(ArmCondType cond) {
        this.cond = cond;
    }

    public void addRegDef(Operand r) {
        if (!r.IsImm()) {
            regDef.add((Reg) r);
        }
    }

    @Override
    public abstract String toString();

    @Override
    public INode<ArmInst, ArmBlock> getINode() {
        return inode;
    }
}
