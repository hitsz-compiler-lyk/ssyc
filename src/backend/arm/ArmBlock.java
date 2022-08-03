package backend.arm;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import backend.operand.Reg;
import utils.IList;
import utils.IListOwner;
import utils.INode;
import utils.INodeOwner;

public class ArmBlock implements IListOwner<ArmInst, ArmBlock>, INodeOwner<ArmBlock, ArmFunction> {
    public static class BlockLiveInfo {
        private Set<Reg> liveUse, liveDef, liveIn, liveOut;

        BlockLiveInfo() {
            this.liveUse = new HashSet<>();
            this.liveDef = new HashSet<>();
            this.liveIn = new HashSet<>();
            this.liveOut = new HashSet<>();
        }

        public void clear() {
            this.liveUse.clear();
            this.liveDef.clear();
            this.liveIn.clear();
            this.liveOut.clear();
        }

        public Set<Reg> getLiveUse() {
            return liveUse;
        }

        public Set<Reg> getLiveDef() {
            return liveDef;
        }

        public Set<Reg> getLiveIn() {
            return liveIn;
        }

        public Set<Reg> getLiveOut() {
            return liveOut;
        }

        public void setLiveUse(Set<Reg> liveUse) {
            this.liveUse = liveUse;
        }

        public void setLiveDef(Set<Reg> liveDef) {
            this.liveDef = liveDef;
        }

        public void setLiveIn(Set<Reg> liveIn) {
            this.liveIn = liveIn;
        }

        public void setLiveOut(Set<Reg> liveOut) {
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
        this.pred = new ArrayList<ArmBlock>();
        this.insts = new IList<ArmInst, ArmBlock>(this);
        this.inode = new INode<ArmBlock, ArmFunction>(this);
        this.blockLiveInfo = new BlockLiveInfo();
        func.asElementView().add(this);
    }

    public ArmBlock(String label) {
        this.label = label;
        this.pred = new ArrayList<>();
        this.insts = new IList<>(this);
    }

}
