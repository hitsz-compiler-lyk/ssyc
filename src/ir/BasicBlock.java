package ir;

import frontend.SourceCodeSymbol;
import ir.analysis.AnalysisInfo;
import ir.analysis.AnalysisInfoOwner;
import ir.inst.*;
import ir.type.IRType;
import utils.*;

import java.util.*;
import java.util.stream.Collectors;

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
        func.add(bb);
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
        add(inst);
    }

    public void addInstAfterPhi(Instruction inst) {
        phiEnd.add(inst);
    }

    public void setBr(BasicBlock nextBB) {
        Log.ensure(!isTerminated(), "Can NOT add Br to terminated block");
        add(new BrInst(this, nextBB));
    }

    public void setBrCond(Value cond, BasicBlock trueBB, BasicBlock falseBB) {
        Log.ensure(!isTerminated(), "Can NOT add BrCond to terminated block");
        add(new BrCondInst(this, cond, trueBB, falseBB));
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
        ensure(instCnt >= 1, "A basic block must at least have one instruction");
        return instructions.listIterator(instCnt - 1);
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

    public void removePredecessor(int index) {
        removeOperandCO(index);
    }

    public void removePredecessor(BasicBlock predecessor) {
        removeOperandCO(predecessor);
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
    public void replacePredecessor(BasicBlock oldPred, BasicBlock newPred) {
        final var index = getPredecessors().indexOf(oldPred);
        ensure(index >= 0, "%s is NOT a predecessor of %s".formatted(oldPred, this));

        replaceOperandCO(index, newPred);
    }

    public void resetPredecessorsOrder(List<BasicBlock> newPredecessorsOrder) {
        final var newPreds = new HashSet<>(newPredecessorsOrder);
        final var oldPreds = new HashSet<>(getPredecessors());
        Log.ensure(newPreds.equals(oldPreds), "Predecessors are different");

        removeOperandAllCO();
        newPredecessorsOrder.forEach(this::addOperandCO);
    }


    //========================================== Value/User 相关 =====================================================//

    public void freeAll() {
        freeFromUseDef();
        freeFromIList();

        if (!instructions.isEmpty()) {
            Log.info("Free all on non-empty basic block: " + this);
            instructions.forEach(Instruction::freeAll);
        }
    }

    public void freeAllWithoutCheck() {
        Log.info("Calling free all WITHOUT check on " + this);
        removeOperandAllCO();
        freeFromIList();

        // 因为这是不检查版本, 块内的指令很有可能还在互相引用的状态, 直接调用 freeAll 会检查不通过
        // 而不需要在调用 freeFromList 了 (它们所在的 IList 本身(this) 都要没了, 调用也没意义了)
        instructions.forEach(Instruction::freeFromUseDefUncheck);
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
        // 最后必须有 Terminator
        ensure(isTerminated(), "Basic block must have a terminator at the end");
        // Phi 跟 Terminator 不能在中间
        for (final var inst : nonPhiAndTerminator()) {
            ensureNot(inst instanceof PhiInst, "Phi shouldn't appearance in the middle of the basic block");
            ensureNot(inst.getKind().isBr(), "Terminator shouldn't appearance in the middle of the basic block");
        }

        ensure(IteratorTools.isUnique(getPredecessors()), "Predecessors of basic block should be unique");

        try {
            getIList().verify();
        } catch (IListException e) {
            throw IRVerifyException.create(this, "IList exception", e);
        }

        try {
            getINode().verify();
        } catch (IListException e) {
            throw IRVerifyException.create(this, "INode exception", e);
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
