package top.origami404.ssyc.ir;

import java.util.*;

import top.origami404.ssyc.ir.analysis.AnalysisInfo;
import top.origami404.ssyc.ir.analysis.AnalysisInfoOwner;
import top.origami404.ssyc.ir.inst.*;
import top.origami404.ssyc.ir.type.IRType;
import top.origami404.ssyc.utils.*;

public class BasicBlock extends Value
    implements IListOwner<Instruction, BasicBlock>, INodeOwner<BasicBlock, Function>,
        AnalysisInfoOwner
{
    public static BasicBlock createFreeBBlock(Function func, String labelName) {
        return new BasicBlock(func, labelName);
    }

    public static BasicBlock createBBlockCO(Function func, String labelName) {
        final var bb = createFreeBBlock(func, labelName);
        func.getIList().asElementView().add(bb);
        return bb;
    }

    public BasicBlock(Function func, String labelName) {
        // 在生成 while 或者是 if 的时候, bblock 经常会有自己的带独特前缀的名字
        // 比如 _cond_1, _if_23 之类的
        // 所以对 BasicBlock 保留带 name 的构造函数

        super(IRType.BBlockTy);
        super.setName("%" + labelName);

        this.instructions = new IList<>(this);
        this.inode = new INode<>(this, func.getIList());
        // 可以在以后再加入对应 parent 的位置, 便于 IR 生成
        // func.getIList().asElementView().add(this);

        this.phiEnd = instructions.asElementView().listIterator();
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
        phiEnd.add(phi);
    }

    public Iterator<PhiInst> iterPhis() {
        return IteratorTools.iterConvert(
            IteratorTools.iterBetweenFromBegin(instructions.asElementView(), phiEnd),
            inst -> {
                ensure(inst instanceof PhiInst, "Non-phi instruction shouldn't appearance between phis");
                assert inst instanceof PhiInst;
                return (PhiInst) inst;
            });
    }

    public Iterable<PhiInst> phis() {
        return this::iterPhis;
    }

    public Iterable<Instruction> nonPhis() {
        return () -> IteratorTools.iterBetweenToEnd(instructions.asElementView(), phiEnd);
    }

    public Iterable<Instruction> nonTerminator() {
        return () -> IteratorTools.iterBetweenFromBegin(instructions.asElementView(), lastButNoTerminator());
    }

    public Iterable<Instruction> nonPhiAndTerminator() {
        return () -> IteratorTools.iterBetween(instructions.asElementView(), phiEnd, lastButNoTerminator());
    }

    public Iterable<Instruction> allInst() { return instructions; }

    public Instruction getTerminator() {
        return instructions.asElementView().get(getInstructionCount() - 1);
    }

    private ListIterator<Instruction> lastButNoTerminator() {
        final var instCnt = getInstructionCount();
        if (instCnt > 1) {
            return instructions.asElementView().listIterator(getInstructionCount() - 2);
        } else {
            return IteratorTools.emptyIter();
        }
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

    @Override
    public void verify() throws IRVerifyException {
        super.verify();
        ensure(getName().charAt(0) == '%', "Name of basic block must begin with '@'");
        ensure(getParent().isPresent(), "Basic block must have parent");

        // 指令检查
        // 条数: 至少一条 (Terminator)
        ensure(getInstructionCount() >= 1, "A basic block should have at least one instruction.");
        // Phi 必须在最前面
        for (final var phi : phis()) {
            // 啥也不干: 在消耗 phis() 迭代器的过程中检查就做完了
        }
        // Phi 跟 Terminator 不能在中间
        for (final var inst : nonPhiAndTerminator()) {
            ensureNot(inst instanceof PhiInst, "Phi shouldn't appearance in the middle of the basic block");
            ensureNot(inst.getKind().isBr(), "Terminator shouldn't appearance in the middle of the basic block");
        }
        // 最后必须有 Terminator
        ensure(isTerminated(), "Basic block must have a terminator at the end");

        ensure(IteratorTools.isUnique(getPredecessors()), "Predecessors of basic block should be unique");

        try {
            getIList().verify();
        } catch (IListException e) {
            throw new IRVerifyException(this, "IList exception", e);
        }

        try {
            getINode().verify();
        } catch (IListException e) {
            throw new IRVerifyException(this, "INode exception", e);
        }
    }

    @Override
    public void verifyAll() throws IRVerifyException {
        super.verifyAll();
        for (final var inst : instructions.asElementView()) {
            inst.verifyAll();
        }
    }

    private Optional<Instruction> getLastInstruction() {
        if (instructions.getSize() == 0) {
            return Optional.empty();
        } else {
            return Optional.of(instructions.asElementView().get(instructions.getSize() - 1));
        }
    }

    String getLabelName() {
        // 去除最前面的 '%'
        return getName().substring(1);
    }

    private IList<Instruction, BasicBlock> instructions;
    private ListIterator<Instruction> phiEnd;
    private INode<BasicBlock, Function> inode;
    private Map<String, AnalysisInfo> analysisInfos;
    private List<BasicBlock> predecessors;
}
