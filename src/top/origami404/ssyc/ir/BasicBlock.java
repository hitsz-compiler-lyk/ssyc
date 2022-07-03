package top.origami404.ssyc.ir;

import java.util.*;

import top.origami404.ssyc.ir.analysis.AnalysisInfo;
import top.origami404.ssyc.ir.analysis.AnalysisInfoOwner;
import top.origami404.ssyc.ir.inst.*;
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

    public BasicBlock(Function func, String name) {
        // 在生成 while 或者是 if 的时候, bblock 经常会有自己的带独特前缀的名字
        // 比如 _cond_1, _if_23 之类的
        // 所以对 BasicBlock 保留带 name 的构造函数

        super(IRType.BBlockTy);

        this.instructions = new IList<>(this);
        func.getIList().asElementView().add(this);

        this.phiEnd = instructions.asINodeView().listIterator();
        this.name = name;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

        return getLastInstruction().map(lastInst -> {
            if (lastInst instanceof BrInst) {
                final var bc = (BrInst) lastInst;
                return List.of(bc.getNextBB());
            } else if (lastInst instanceof BrCondInst) {
                final var brc = (BrCondInst) lastInst;
                return List.of(brc.getTrueBB(), brc.getFalseBB());
            } else {
                return new ArrayList<BasicBlock>();
            }
        }).orElse(List.of());
    }

    public List<BasicBlock> getPredecessors() {
        return predecessors;
    }

    public void addPredecessor(BasicBlock predecessor) {
        predecessors.add(predecessor);
    }

    public boolean isTerminated() {
        return getLastInstruction().map(Instruction::getKind).map(InstKind::isBr).orElse(false);
    }

    private Optional<Instruction> getLastInstruction() {
        if (instructions.getSize() == 0) {
            return Optional.empty();
        } else {
            return Optional.of(instructions.asElementView().get(instructions.getSize() - 1));
        }
    }

    private static int bblockNo = 0;

    private String name;
    private IList<Instruction, BasicBlock> instructions;
    private ListIterator<INode<Instruction, BasicBlock>> phiEnd;
    private INode<BasicBlock, Function> inode;
    private Map<String, AnalysisInfo> analysisInfos;
    private List<BasicBlock> predecessors;
}
