package pass.ir.loop;

import ir.BasicBlock;
import ir.Value;
import ir.constant.IntConst;
import pass.ir.loop.CanonicalLoop.CanonicalLoopUpdater;
import utils.CollectionTools;
import utils.Log;

import java.util.*;

public class FullyUnroll implements LoopPass {
    @Override
    public void runOnLoop(final CanonicalLoop canonicalLoop) {
        final var loopOpt = Optional.of(canonicalLoop)
            .flatMap(ForLoop::tryConvertFromOpt)
            .flatMap(FullyUnrollableLoop::tryCreate);

        if (loopOpt.isEmpty()) {
            return;
        }

        // 具体过程可参考 LoopUnroll 的注释, 本文件中只包含差异部分的注释

        final var loop = loopOpt.get();
        final var tripCount = loop.getTripCount();

        final var preHeader = loop.canonical().getPreHeader();
        final var oldHeader = loop.canonical().getHeader();
        final var exit = loop.canonical().getUniqueExit();

        // 如果循环根本一次都不执行的话
        if (tripCount <= 0) {
            // 直接删除原来的循环
            // 这时候只需要把 phi 删光, 其它部分要留下, 因为之后的循环外的指令有可能会用到 header 中定义的东西
            oldHeader.phis().forEach(phi -> {
                phi.replaceAllUseWith(phi.getIncomingValue(0));
                phi.freeAll();
            });
            oldHeader.adjustPhiEnd();
            // 删除原来的 body 到自己的前继
            oldHeader.removePredecessor(1);

            // 循环体自己删光
            final var blocksToRemove = canonicalLoop.getBody();

            final var updater = new CanonicalLoopUpdater(Set.of(), blocksToRemove);
            canonicalLoop.getParent().ifPresent(updater::update);

            blocksToRemove.forEach(BasicBlock::freeAllWithoutCheck);

            // 去掉原来的条件跳转, 直接改成直接跳转到 exit
            oldHeader.getTerminator().freeAll();
            oldHeader.setBr(exit);

            return;
        }

        final var newHeaderCreator = new FullyUnrollHeaderCreator(loop);
        final var newHeader = newHeaderCreator.create();

        final var unrolled = new ArrayList<Set<BasicBlock>>();

        IndexBlockCreator lastIndexBlockCreator = FullyUnrollIndexBlockCreator.createFromFullyUnrollHeaderCreator(newHeaderCreator);
        newHeaderCreator.setBranch(lastIndexBlockCreator.getNewIndexBlock());

        ForBodyCloner lastForBodyCloner = null;

        for (var currUnrollCount = 0; currUnrollCount < tripCount; currUnrollCount++) {
            lastForBodyCloner = new ForBodyCloner(lastIndexBlockCreator);
            unrolled.add(lastForBodyCloner.convert());

            // 直接在最后多生成一个 index block 来获取最后一趟循环体之后的, 旧循环头里的值的对应物
            lastIndexBlockCreator = IndexBlockCreator.createFromLast(lastForBodyCloner);
            lastForBodyCloner.getNewLatch().setBr(lastIndexBlockCreator.getNewIndexBlock());
        }

        // 让最后新生成的那个 index block 跳转到 exit
        final var lastIndex = lastIndexBlockCreator.getNewIndexBlock();
        lastIndex.setBr(exit);
        exit.removePredecessor(oldHeader); // 来自 latch 的 pred 已经在 BrInst 构造函数中设了

        // 让 preHeader 跳转到自己
        preHeader.getTerminator().replaceOperandCO(oldHeader, newHeader);
        newHeader.addPredecessor(preHeader);

        // 先留下原来的 header 的位置, 先插完
        final var function = oldHeader.getParent();
        final var headerPosition = function.indexOf(oldHeader);

        // 保存统一一下要加入的各种块
        final var blocksToAdd = new LinkedHashSet<BasicBlock>();
        blocksToAdd.add(newHeader);
        unrolled.forEach(blocksToAdd::addAll);
        blocksToAdd.add(lastIndex);

        function.addAll(headerPosition, blocksToAdd);

        // 再完全删除原来的循环
        // 把对原 header 中任何值的引用都换成对最后一次循环体中的值的的引用
        // 因为这里会修改原来的指令, 而指令的 hashCode 与操作数有关, 所以要先弄一个备份出来
        final var identMap = CollectionTools.createIdentifierMap(lastIndexBlockCreator.getAllReplacement());
        oldHeader.nonTerminator().forEach(oldInst -> {
            final var newValue = identMap.get(System.identityHashCode(oldInst));
            oldInst.replaceAllUseWith(newValue);
        });
        loop.canonical().getAll().forEach(BasicBlock::freeAllWithoutCheck);

        // 要将这个循环完全删除
        canonicalLoop.getParent().ifPresent(parentLoop -> {
            final var blocksToRemove = new LinkedHashSet<>(loop.canonical().getAll());
            new CanonicalLoopUpdater(blocksToAdd, blocksToRemove).update(parentLoop);
            parentLoop.removeSubLoop(canonicalLoop);
        });
    }

}

class FullyUnrollableLoop {
    public static Optional<FullyUnrollableLoop> tryCreate(ForLoop loop) {
        return Optional.ofNullable(loop)
            .filter(FullyUnrollableLoop::canFullyUnroll)
            .map(FullyUnrollableLoop::new);
    }

    public static boolean canFullyUnroll(ForLoop loop) {
        final var begin = loop.getBegin();
        final var end   = loop.getEnd();
        final var step  = loop.getStep();

        if (begin instanceof IntConst && end instanceof IntConst && step instanceof IntConst) {
            final var begInt = ((IntConst) begin).getValue();
            final var endInt = ((IntConst) end).getValue();
            final var stepInt = ((IntConst) step).getValue();

            // 负数先不展!
            if (endInt - begInt < 0 && stepInt <= 0) {
                return false;
            }

            final var tripCount = divideCeil(endInt - begInt, stepInt);
            final var instCount = loop.canonical().getBody().stream().mapToInt(List::size).sum();

            // 防止溢出!!! thu-test 81-powmod 最后一个!!!
            return tripCount <= 2000 && instCount <= 2000 && tripCount * instCount <= 2000;
        }

        return false;
    }

    static int divideCeil(int numerator, int denominator) {
        // TODO: 考虑溢出等情况
        // TODO: 考虑负数等情况
        Log.ensure(!(numerator < 0 && denominator < 0));
        return (numerator + denominator - 1) / denominator;
    }

    public int getBegin() {
        return begin;
    }

    public int getEnd() {
        return end;
    }

    public int getStep() {
        return step;
    }

    public int getTripCount() {
        return divideCeil(end - begin, step);
    }

    public ForLoop forLoop() {
        return loop;
    }

    public CanonicalLoop canonical() {
        return loop.canonical();
    }

    private final ForLoop loop;
    private final int begin;
    private final int end;
    private final int step;

    FullyUnrollableLoop(ForLoop loop) {
        this.loop   = loop;
        this.begin  = ((IntConst) loop.getBegin()).getValue();
        this.end    = ((IntConst) loop.getEnd()).getValue();
        this.step   = ((IntConst) loop.getStep()).getValue();
    }
}

class FullyUnrollHeaderCreator extends NewHeaderCreator {
    public FullyUnrollHeaderCreator(FullyUnrollableLoop loop) {
        super(loop.forLoop(), loop.getTripCount());
    }

    @Override
    public BasicBlock create() {
        final var header = loop.canonical().getHeader();
        final var symbol = header.getSymbol().newSymbolWithSuffix("_fully_header");
        final var block = BasicBlock.createFreeBBlock(symbol);

        for (final var phi : header.phis()) {
            final var init = phi.getIncomingValue(0);
            oldToNew.put(phi, init);
        }

        header.nonPhiAndTerminator().stream().map(this::getOrCreate).forEach(block::add);
        block.adjustPhiEnd();

        this.block = block;
        return block;
    }

    @Override
    public void setBranch(final BasicBlock firstIndexBlock) {
        block.setBr(firstIndexBlock);
    }

    Map<Value, Value> getOldToNewRaw() {
        return oldToNew;
    }

    Value getRaw(Value old) {
        return oldToNew.get(old);
    }

    private BasicBlock block;
}

class FullyUnrollIndexBlockCreator extends IndexBlockCreator {
    public static FullyUnrollIndexBlockCreator createFromFullyUnrollHeaderCreator(FullyUnrollHeaderCreator newHeaderCreator) {
        final var loop = newHeaderCreator.getLoop();

        final var creator = new FullyUnrollIndexBlockCreator(loop, 0);
        for (final var phi : loop.canonical().getHeader().phis()) {
            final var newValue = newHeaderCreator.getRaw(phi);
            creator.oldToNew.put(phi, newValue);
        }

        return creator;
    }

    protected FullyUnrollIndexBlockCreator(ForLoop loop, int unrollCount) {
        super(loop, unrollCount);
    }
}