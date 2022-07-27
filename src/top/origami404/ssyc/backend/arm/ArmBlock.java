package top.origami404.ssyc.backend.arm;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import top.origami404.ssyc.backend.operand.Operand;
import top.origami404.ssyc.utils.IList;
import top.origami404.ssyc.utils.IListOwner;
import top.origami404.ssyc.utils.INode;
import top.origami404.ssyc.utils.INodeOwner;

public class ArmBlock implements IListOwner<ArmInst, ArmBlock>, INodeOwner<ArmBlock, ArmFunction> {
    public static class BlockLiveInfo {
        private Set<Operand> liveUse, liveDef, liveIn, liveOut;

        BlockLiveInfo() {
            this.liveUse = new HashSet<>();
            this.liveDef = new HashSet<>();
            this.liveIn = new HashSet<>();
            this.liveOut = new HashSet<>();
        }

        public Set<Operand> getLiveUse() {
            return liveUse;
        }

        public Set<Operand> getLiveDef() {
            return liveDef;
        }

        public Set<Operand> getLiveIn() {
            return liveIn;
        }

        public Set<Operand> getLiveOut() {
            return liveOut;
        }

        public void setLiveUse(Set<Operand> liveUse) {
            this.liveUse = liveUse;
        }

        public void setLiveDef(Set<Operand> liveDef) {
            this.liveDef = liveDef;
        }

        public void setLiveIn(Set<Operand> liveIn) {
            this.liveIn = liveIn;
        }

        public void setLiveOut(Set<Operand> liveOut) {
            this.liveOut = liveOut;
        }
    }

    private String label;

    private List<ArmBlock> pred;

    private ArmBlock trueSuccBlock, falseSuccBlock;

    private INode<ArmBlock, ArmFunction> inode;

    private IList<ArmInst, ArmBlock> insts;

    private BlockLiveInfo blockLiveInfo;

    @Override
    public String toString() {
        return "";
    }

    @Override
    public INode<ArmBlock, ArmFunction> getINode() {
        return inode;
    }

    @Override
    public IList<ArmInst, ArmBlock> getIList() {
        return insts;
    }

    public ArmBlock getTrueSuccBlock() {
        return trueSuccBlock;
    }

    public ArmBlock getFalseSuccBlock() {
        return falseSuccBlock;
    }

    public void setTrueSuccBlock(ArmBlock trueSuccBlock) {
        this.trueSuccBlock = trueSuccBlock;
    }

    public void setFalseSuccBlock(ArmBlock falseSuccBlock) {
        this.falseSuccBlock = falseSuccBlock;
    }

    public List<ArmBlock> getSucc() {
        var ret = new ArrayList<ArmBlock>();
        if (trueSuccBlock != null) {
            ret.add(trueSuccBlock);
        }
        if (falseSuccBlock != null) {
            ret.add(falseSuccBlock);
        }
        return ret;
    }

    public BlockLiveInfo getBlockLiveInfo() {
        return blockLiveInfo;
    }

    public String getLabel() {
        return label;
    }

    public List<ArmBlock> getPred() {
        return pred;
    }

    public void addPred(ArmBlock pred) {
        this.pred.add(pred);
    }

    public ArmBlock(ArmFunction func, String label) {
        this.label = label;
<<<<<<< HEAD
        this.pred = new ArrayList<ArmBlock>();
        this.insts = new IList<ArmInst, ArmBlock>(this);
        this.inode = new INode<>(this, func);
        func.asElementView().add(this);
=======
        func.asElementView().add(this);
        this.pred = new ArrayList<>();
        this.insts = new IList<>(this);
>>>>>>> dfac878edd9308c304b5b7283c261c24dbc74992
    }

    public ArmBlock(String label) {
        this.label = label;
        this.pred = new ArrayList<>();
        this.insts = new IList<>(this);
    }

}
