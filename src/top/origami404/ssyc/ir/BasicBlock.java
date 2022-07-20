package top.origami404.ssyc.ir;

import java.util.*;

import top.origami404.ssyc.frontend.SourceCodeSymbol;
import top.origami404.ssyc.ir.analysis.AnalysisInfo;
import top.origami404.ssyc.ir.analysis.AnalysisInfoOwner;
import top.origami404.ssyc.ir.inst.*;
import top.origami404.ssyc.ir.type.IRType;
import top.origami404.ssyc.utils.*;

public class BasicBlock extends Value
    implements IListOwner<Instruction, BasicBlock>, INodeOwner<BasicBlock, Function>,
        AnalysisInfoOwner
{
    public static BasicBlock createFreeBBlock(Function func, SourceCodeSymbol symbol) {
        return new BasicBlock(func, symbol);
    }

    public static BasicBlock createBBlockCO(Function func, SourceCodeSymbol symbol) {
        final var bb = createFreeBBlock(func, symbol);
        func.getIList().add(bb);
        return bb;
    }

    public BasicBlock(Function func, SourceCodeSymbol symbol) {
        super(IRType.BBlockTy);
        super.setSymbol(symbol);

        this.instructions = new IList<>(this);
        this.inode = new INode<>(this, func);
        // 可以在以后再加入对应 parent 的位置, 便于 IR 生成
        // func.getIList().add(this);

        this.phiEnd = instructions.listIterator();
        this.predecessors = new ArrayList<>();

        this.analysisInfos = new HashMap<>();
    }

    public IList<Instruction, BasicBlock> getIList() {
        return instructions;
    }

    public INode<BasicBlock, Function> getINode() {
        return inode;
    }

    public int getInstructionCount() {
        return instructions.size();
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
            IteratorTools.iterBetweenFromBegin(instructions, phiEnd),
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
        return () -> IteratorTools.iterBetweenToEnd(instructions, phiEnd.clone());
    }

    public Iterable<Instruction> nonTerminator() {
        return () -> IteratorTools.iterBetweenFromBegin(instructions, lastButNoTerminator());
    }

    public Iterable<Instruction> nonPhiAndTerminator() {
        return () -> IteratorTools.iterBetween(instructions, phiEnd, lastButNoTerminator());
    }

    public Iterable<Instruction> allInst() { return instructions; }

    public Instruction getTerminator() {
        return instructions.get(getInstructionCount() - 1);
    }

    private ListIterator<Instruction> lastButNoTerminator() {
        final var instCnt = getInstructionCount();
        if (instCnt >= 1) {
            return instructions.listIterator(instCnt - 1);
        } else {
            return instructions.listIterator();
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

    public void removePredecessorWithPhiUpdated(BasicBlock predecessorToBeRemoved) {
        final var index = predecessors.indexOf(predecessorToBeRemoved);

        ensure(index >= 0, "BBlock %s is NOT the predecessor of %s".formatted(predecessorToBeRemoved, this));

        predecessors.remove(index);
        for (final var phi : phis()) {
            phi.removeOperandCO(index);
        }

        if (predecessors.size() == 0) {
            Log.info("Eliminate BBlock " + this);
        }
    }

    public boolean isTerminated() {
        return getLastInstruction().map(Instruction::getKind).map(InstKind::isBr).orElse(false);
    }

    @Override
    public void replaceAllUseWith(final Value newValue) {
        super.replaceAllUseWith(newValue);

        final var func = getParentOpt().orElseThrow(() -> new IRVerifyException(this, "Free block"));
        ensure(newValue instanceof BasicBlock, "Can NOT use non-BBlock to replace a bblock");
        func.getIList().replaceFirst(this, (BasicBlock) newValue);
    }

    @Override
    public void verify() throws IRVerifyException {
        super.verify();
        ensure(getSymbolOpt().isPresent(), "A basic block must own a symbol");
        ensure(getParentOpt().isPresent(), "Basic block must have parent");

        // 指令检查
        // 条数: 至少一条 (Terminator)
        ensure(getInstructionCount() >= 1, "A basic block should have at least one instruction.");
        // Phi 必须在最前面
        for (final var phi : phis()) {
            // 啥也不干: 在消耗 phis() 迭代器的过程中检查就做完了
            assert phi != null;
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
        for (final var inst : instructions) {
            inst.verifyAll();
        }
    }

    private Optional<Instruction> getLastInstruction() {
        if (instructions.size() == 0) {
            return Optional.empty();
        } else {
            return Optional.of(instructions.get(instructions.size() - 1));
        }
    }

    public String getLabelName() {
        // 去除最前面的 '%'
        return getSymbol().getName();
    }

    private final IList<Instruction, BasicBlock> instructions;
    private IList<Instruction, BasicBlock>.IListElementIterator phiEnd;
    private final INode<BasicBlock, Function> inode;
    private final Map<String, AnalysisInfo> analysisInfos;
    private final List<BasicBlock> predecessors;

    public void adjustPhiEnd() {
        // 不可能一条指令都没有, 因为还要有最后的 terminator
        int i = 0;
        while (instructions.get(i).is(PhiInst.class)) {
            i += 1;
        }

        phiEnd = instructions.listIterator(i);
    }
}
