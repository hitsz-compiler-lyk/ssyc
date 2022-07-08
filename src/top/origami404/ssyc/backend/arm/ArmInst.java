package top.origami404.ssyc.backend.arm;

import java.util.ArrayList;

import top.origami404.ssyc.backend.operand.Operand;
import top.origami404.ssyc.backend.operand.Reg;
import top.origami404.ssyc.utils.INode;
import top.origami404.ssyc.utils.INodeOwner;

public class ArmInst implements INodeOwner<ArmInst, ArmBlock> {

    public enum ArmInstKind {
        IAdd, ISub, IRsb, IMul, IDiv,
        FAdd, FSub, FMul, FDiv,

        INeg, FNeg,

        MOV,
        LOAD,
    }

    public enum ArmCondType {
        Any,
        Ge, Gt, Eq, Ne, Le, Lt,
    }

    public static String toString(ArmCondType cond) {
        switch (cond) {
            case Ge:
                return "ge";
            case Gt:
                return "gt";
            case Eq:
                return "eq";
            case Ne:
                return "ne";
            case Le:
                return "le";
            case Lt:
                return "lt";
            default:
                return "";
        }
    }

    private ArmInstKind inst;
    private INode<ArmInst, ArmBlock> inode;
    private ArrayList<Reg> regUse, regDef;

    public ArmInst(ArmInstKind inst) {
        this.inst = inst;
        this.inode = new INode<>(this);
        this.regUse = new ArrayList<Reg>();
        this.regDef = new ArrayList<Reg>();
    }

    public ArmInstKind getInst() {
        return inst;
    }

    public ArrayList<Reg> getRegUse() {
        return regUse;
    }

    public ArrayList<Reg> getRegDef() {
        return regDef;
    }

    public void addRegUse(Operand r) {
        if (!r.IsImm()) {
            regUse.add((Reg) r);
        }
    }

    public void addRegDef(Operand r) {
        if (!r.IsImm()) {
            regDef.add((Reg) r);
        }
    }

    @Override
    public INode<ArmInst, ArmBlock> getINode() {
        return inode;
    }
}
