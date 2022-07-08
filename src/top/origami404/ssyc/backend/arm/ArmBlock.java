package top.origami404.ssyc.backend.arm;

import java.util.ArrayList;
import java.util.HashSet;

import top.origami404.ssyc.backend.operand.Operand;
import top.origami404.ssyc.utils.IList;
import top.origami404.ssyc.utils.IListOwner;
import top.origami404.ssyc.utils.INode;
import top.origami404.ssyc.utils.INodeOwner;

public class ArmBlock implements IListOwner<ArmInst, ArmBlock>, INodeOwner<ArmBlock, ArmFunction> {
    public static class BlockLiveInfo {
        private HashSet<Operand> liveUse, liveDef, liveIn, liveOut;

        BlockLiveInfo() {
            this.liveUse = new HashSet<Operand>();
            this.liveDef = new HashSet<Operand>();
            this.liveIn = new HashSet<Operand>();
            this.liveOut = new HashSet<Operand>();
        }

        public HashSet<Operand> getLiveUse() {
            return liveUse;
        }

        public HashSet<Operand> getLiveDef() {
            return liveDef;
        }

        public HashSet<Operand> getLiveIn() {
            return liveIn;
        }

        public HashSet<Operand> getLiveOut() {
            return liveOut;
        }

        public void setLiveUse(HashSet<Operand> liveUse) {
            this.liveUse = liveUse;
        }

        public void setLiveDef(HashSet<Operand> liveDef) {
            this.liveDef = liveDef;
        }

        public void setLiveIn(HashSet<Operand> liveIn) {
            this.liveIn = liveIn;
        }

        public void setLiveOut(HashSet<Operand> liveOut) {
            this.liveOut = liveOut;
        }
    }

    private String label;

    private ArrayList<ArmBlock> pred;

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

    public ArrayList<ArmBlock> getSucc() {
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

    public ArrayList<ArmBlock> getPred() {
        return pred;
    }

    public void addPred(ArmBlock pred) {
        this.pred.add(pred);
    }

    public ArmBlock(ArmFunction func, String label) {
        this.label = label;
        func.asElementView().add(this);
        this.pred = new ArrayList<ArmBlock>();
        this.insts = new IList<ArmInst, ArmBlock>(this);
    }

    public ArmBlock(String label) {
        this.label = label;
        this.pred = new ArrayList<ArmBlock>();
        this.insts = new IList<ArmInst, ArmBlock>(this);
    }

}
