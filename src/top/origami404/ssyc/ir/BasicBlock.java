package top.origami404.ssyc.ir;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;

import top.origami404.ssyc.ir.analysis.AnalysisInfo;
import top.origami404.ssyc.ir.analysis.AnalysisInfoOwner;
import top.origami404.ssyc.ir.inst.BrCondInst;
import top.origami404.ssyc.ir.inst.BrInst;
import top.origami404.ssyc.ir.inst.Instruction;
import top.origami404.ssyc.ir.inst.PhiInst;
import top.origami404.ssyc.ir.type.IRType;
import top.origami404.ssyc.utils.IList;
import top.origami404.ssyc.utils.IListOwner;
import top.origami404.ssyc.utils.INode;
import top.origami404.ssyc.utils.INodeOwner;

public class BasicBlock extends Value
    implements IListOwner<Instruction, BasicBlock>, INodeOwner<BasicBlock, Function>,
        AnalysisInfoOwner
{
    public BasicBlock(Function func) {
        this(func, "_" + bblockNo++);
    }

    public BasicBlock(Function func, String labelName) {
        // 在生成 while 或者是 if 的时候, bblock 经常会有自己的带独特前缀的名字
        // 比如 _cond_1, _if_23 之类的
        // 所以对 BasicBlock 保留带 name 的构造函数

        super(IRType.BBlockTy);
        super.setName("%" + labelName);

        this.instructions = new IList<>(this);
        func.getIList().asElementView().add(this);

        this.phiEnd = instructions.asINodeView().listIterator();
        this.predecessors = new ArrayList<>();
    }

    public IList<Instruction, BasicBlock> getIList() {
        return instructions;
    }

    public INode<BasicBlock, Function> getINode() {
        return inode;
    }

    public int getInstructionCount() {
        return instructions.getSize();
    }

    @Override
    public Map<String, AnalysisInfo> getInfoMap() {
        return analysisInfos;
    }

    public void addPhi(PhiInst phi) {
        phiEnd.add(phi.getINode());
    }

    public Iterator<PhiInst> iterPhis() {
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return iter.hasNext() && iter != phiEnd;
            }

            @Override
            public PhiInst next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }

                final var inst = this.iter.next().getOwner();
                if (inst instanceof PhiInst) {
                    return (PhiInst) inst;
                } else {
                    throw new RuntimeException("Non-phi instruction appearances before phi");
                }
            }

            ListIterator<INode<Instruction, BasicBlock>> iter
                = instructions.asINodeView().listIterator();
        };
    }

    public List<BasicBlock> getSuccessors() {
        // 使用 List 以方便有可能需要使用索引标记的情况
        // 比如说用 List 就可以直接开 boolean visited[N]
        // 而不用开 Map<BasicBlock, Boolean> visited

        final var lastInst = instructions.asElementView().get(instructions.getSize());
        if (lastInst instanceof BrInst) {
            final var bc = (BrInst) lastInst;
            return List.of(bc.getNextBB());
        } else if (lastInst instanceof BrCondInst) {
            final var brc = lastInst.as(BrCondInst.class);
            return List.of(brc.getTrueBB(), brc.getFalseBB());
        } else {
            return List.of();
        }
    }

    public List<BasicBlock> getPredecessors() {
        return predecessors;
    }

    public void addPredecessor(BasicBlock predecessor) {
        predecessors.add(predecessor);
    }

    String getLabelName() {
        // 去除最前面的 '%'
        return getName().substring(1);
    }

    private static int bblockNo = 0;

    private IList<Instruction, BasicBlock> instructions;
    private ListIterator<INode<Instruction, BasicBlock>> phiEnd;
    private INode<BasicBlock, Function> inode;
    private Map<String, AnalysisInfo> analysisInfos;
    private List<BasicBlock> predecessors;
}
