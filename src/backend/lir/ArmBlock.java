package backend.lir;

import backend.lir.inst.ArmInst;
import backend.lir.operand.Addr;
import backend.lir.operand.IImm;
import backend.lir.operand.Imm;
import backend.lir.operand.Reg;
import utils.IList;
import utils.IListOwner;
import utils.INode;
import utils.INodeOwner;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

        public void setLiveOut(Set<Reg> liveOut) {
            this.liveOut = liveOut;
        }
    }

    private final String label;

    private final List<ArmBlock> pred;

    private ArmBlock trueSuccBlock, falseSuccBlock;

    private INode<ArmBlock, ArmFunction> inode;

    private final IList<ArmInst, ArmBlock> insts;

    private final BlockLiveInfo blockLiveInfo;

    private final Set<Addr> haveRecoverAddrs;

    private final Set<IImm> haveRecoverOffset;

    private final Set<IImm> haveRecoverLoadParam;

    private final Set<IImm> haveRecoverStackLoad;

    private final Set<Imm> haveRecoverImm;

    public Set<Addr> getHaveRecoverAddrs() {
        return haveRecoverAddrs;
    }

    public Set<IImm> getHaveRecoverOffset() {
        return haveRecoverOffset;
    }

    public Set<IImm> getHaveRecoverLoadParam() {
        return haveRecoverLoadParam;
    }

    public Set<IImm> getHaveRecoverStackLoad() {
        return haveRecoverStackLoad;
    }

    public Set<Imm> getHaveRecoverImm() {
        return haveRecoverImm;
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
        this(label);
        this.inode = new INode<>(this);
        func.add(this);
    }

    public ArmBlock(String label) {
        this.label = label;
        this.pred = new ArrayList<>();
        this.blockLiveInfo = new BlockLiveInfo();
        this.haveRecoverAddrs = new HashSet<>();
        this.haveRecoverOffset = new HashSet<>();
        this.haveRecoverLoadParam = new HashSet<>();
        this.haveRecoverStackLoad = new HashSet<>();
        this.haveRecoverImm = new HashSet<>();
        this.insts = new IList<>(this);
    }

}
