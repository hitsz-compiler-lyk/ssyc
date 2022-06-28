package top.origami404.ssyc.backend.arm;

import java.util.ArrayList;

import top.origami404.ssyc.backend.operand.Operand;
import top.origami404.ssyc.backend.operand.Reg;
import top.origami404.ssyc.utils.INode;
import top.origami404.ssyc.utils.INodeOwner;

public class ArmInst implements INodeOwner<ArmInst, ArmBlock> {

    public enum ArmInstKind {
        IAdd, ISub, IMul, IDiv,
        FAdd, FSub, FMul, FDiv,
    }

    private ArmInstKind inst;
    private INode<ArmInst, ArmBlock> inode;
    private ArrayList<Reg> regUse = new ArrayList<Reg>();
    private ArrayList<Reg> regDef = new ArrayList<Reg>();

    public ArmInst(ArmInstKind inst) {
        this.inst = inst;
        this.inode = new INode<>(this);
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
