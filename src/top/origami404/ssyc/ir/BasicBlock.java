package top.origami404.ssyc.ir;

import java.util.*;
import java.util.stream.Collectors;

import top.origami404.ssyc.frontend.SourceCodeSymbol;
import top.origami404.ssyc.ir.analysis.AnalysisInfo;
import top.origami404.ssyc.ir.analysis.AnalysisInfoOwner;
import top.origami404.ssyc.ir.inst.*;
import top.origami404.ssyc.ir.type.IRType;
import top.origami404.ssyc.utils.*;

public class BasicBlock extends User
    implements IListOwner<Instruction, BasicBlock>, INodeOwner<BasicBlock, Function>,
        AnalysisInfoOwner
{
    //======================================= 构造基本块 =============================================================//
    public static BasicBlock createFreeBBlock(SourceCodeSymbol symbol) {
        return new BasicBlock(symbol);
    }

    public static BasicBlock createBBlockCO(Function func, SourceCodeSymbol symbol) {
        final var bb = createFreeBBlock(symbol);
        func.getIList().add(bb);
        return bb;
    }

    private BasicBlock(SourceCodeSymbol symbol) {
        super(IRType.BBlockTy);
        super.setSymbol(symbol);

        this.instructions = new IList<>(this);
        this.inode = new INode<>(this);

        this.phiEnd = instructions.listIterator();

        this.analysisInfos = new HashMap<>();
    }


    //============================= 基本块其他成分 (IList, INode, AnalysisOwner) =====================================//
    @Override public IList<Instruction, BasicBlock> getIList() {
        return instructions;
    }

    @Override public INode<BasicBlock, Function> getINode() {
        return inode;
    }

    public int getInstructionCount() {
        return instructions.size();
    }

    @Override public Map<String, AnalysisInfo> getInfoMap() {
        return analysisInfos;
    }


    //============================================ 指令修改 ==========================================================//
    public void addPhi(PhiInst phi) {
        phiEnd.add(phi);
    }

    public void adjustPhiEnd() {
        // 不可能一条指令都没有, 因为还要有最后的 terminator
        int i = 0;
        while (instructions.get(i).is(PhiInst.class)) {
            i += 1;
        }

        phiEnd = instructions.listIterator(i);
    }

    public void addInstBeforeTerminator(Instruction inst) {
        lastButNoTerminator().add(inst);
    }

    public void addInstAtEnd(Instruction inst) {
        getIList().add(inst);
    }


    //============================================ 指令访问 ==========================================================//
    public Iterator<PhiInst> iterPhis() {
        return IteratorTools.iterConvert(
            IteratorTools.iterBetweenFromBegin(instructions, phiEnd),
            inst -> {
                ensure(inst instanceof PhiInst, "Non-phi instruction shouldn't appearance between phis");
                assert inst instanceof PhiInst;
                return (PhiInst) inst;
            });
    }

    public List<PhiInst> phis() {
        return IteratorTools.iterToListView(this::iterPhis);
    }

    public List<Instruction> nonPhis() {
        return IteratorTools.iterToListView(
            () -> IteratorTools.iterBetweenToEnd(instructions, phiEnd.clone()));
    }

    public List<Instruction> nonTerminator() {
        return IteratorTools.iterToListView(
            () -> IteratorTools.iterBetweenFromBegin(instructions, lastButNoTerminator()));
    }

    public List<Instruction> nonPhiAndTerminator() {
        return IteratorTools.iterToListView(
            () -> IteratorTools.iterBetween(instructions, phiEnd.clone(), lastButNoTerminator()));
    }

    public List<Instruction> allInst() {
        return Collections.unmodifiableList(instructions);
    }

    public Instruction getTerminator() {
        return instructions.get(getInstructionCount() - 1);
    }

    public boolean isTerminated() {
        return getLastInstruction().map(Instruction::getKind).map(InstKind::isBr).orElse(false);
    }

    private ListIterator<Instruction> lastButNoTerminator() {
        final var instCnt = getInstructionCount();
        if (instCnt >= 1) {
            return instructions.listIterator(instCnt - 1);
        } else {
            return instructions.listIterator();
        }
    }

    private Optional<Instruction> getLastInstruction() {
        if (instructions.size() == 0) {
            return Optional.empty();
        } else {
            return Optional.of(instructions.get(instructions.size() - 1));
        }
    }


    //========================================== CFG 相关 (前后继访问/修改) ==========================================//
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
        return getOperands().stream().map(BasicBlock.class::cast).collect(Collectors.toList());
    }

    public void addPredecessor(BasicBlock predecessor) {
        addOperandCO(predecessor);
    }

    void removePredecessor(int index) {
        removeOperandCO(index);
    }

    public int getPredecessorSize() {
        return getOperandSize();
    }

    public void removePredecessorWithPhiUpdated(BasicBlock predecessorToBeRemoved) {
        final var index = getPredecessors().indexOf(predecessorToBeRemoved);

        ensure(index >= 0, "BBlock %s is NOT the predecessor of %s".formatted(predecessorToBeRemoved, this));

        removePredecessor(index);
        for (final var phi : phis()) {
            phi.removeOperandCO(index);
        }

        if (getPredecessorSize() == 0) {
            Log.info("Eliminate BBlock " + this);
        }
    }

    /** 不维护新前继的后继是自己 */
    public void replacePredcessor(BasicBlock oldPred, BasicBlock newPred) {
        final var index = getPredecessors().indexOf(oldPred);
        ensure(index >= 0, "oldPred %s is NOT a predcessor of %s".formatted(oldPred, this));

        replaceOperandCO(index, newPred);
    }


    //========================================== Value/User 相关 =====================================================//

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

    private final IList<Instruction, BasicBlock> instructions;
    private IList<Instruction, BasicBlock>.IListElementIterator phiEnd;
    private final INode<BasicBlock, Function> inode;
    private final Map<String, AnalysisInfo> analysisInfos;
}
