package pass.ir.loop;

import ir.BasicBlock;
import ir.Value;
import ir.constant.Constant;
import ir.inst.*;
import pass.ir.util.MultiBasicBlockCloner;
import utils.CollectionTools;
import utils.Log;

import java.util.*;

public class LoopUnroll implements LoopPass {
    @Override
    public void runOnLoop(final CanonicalLoop loop) {
        final var forLoop = ForLoop.tryConvertFrom(loop);

        if (forLoop == null) {
            return;
        }

        final int totalUnrollCount = 4;
        final var preHeader = forLoop.canonical().getPreHeader();
        final var oldHeader = forLoop.canonical().getHeader();

        final var newHeaderCreator = new NewHeaderCreator(forLoop, totalUnrollCount);
        final var newHeader = newHeaderCreator.create();

        final var unrolled = new ArrayList<Set<BasicBlock>>();

        var lastIndexBlockCreator = IndexBlockCreator.createFromNewHeader(newHeaderCreator);
        newHeaderCreator.setBranch(lastIndexBlockCreator.getNewIndexBlock());
        ForBodyCloner lastForBodyCloner = null;

        for (var currUnrollCount = 0; currUnrollCount < totalUnrollCount; currUnrollCount++) {
            // 创建这一轮的 Body
            lastForBodyCloner = new ForBodyCloner(lastIndexBlockCreator);
            // 将这一轮的 body 加入到总的生成块中
            unrolled.add(lastForBodyCloner.convert());

            if (currUnrollCount + 1 < totalUnrollCount) {
                // 创建新一轮的 IndexBlock
                lastIndexBlockCreator = IndexBlockCreator.createFromLast(lastForBodyCloner);
                // 将上一轮的 Latch 跳转到这一轮来
                lastForBodyCloner.getNewLatch().setBr(lastIndexBlockCreator.getNewIndexBlock());
            }
        }

        // 保证 newHeader 的前继的顺序
        preHeader.getTerminator().replaceOperandCO(oldHeader, newHeader);
        newHeader.addPredecessor(preHeader);
        lastForBodyCloner.getNewLatch().setBr(newHeader);

        final var phiSize = newHeader.phis().size();
        Log.ensure(phiSize == oldHeader.phis().size());
        // Canonical loop 保证了第 0 个前继必定是 pre-header
        Log.ensure(oldHeader.getPredecessors().get(0) == preHeader);
        oldHeader.replacePredecessor(preHeader, newHeader);


        final ForBodyCloner finalLastForBodyCloner = lastForBodyCloner;
        CollectionTools.zip(oldHeader.phis(), newHeader.phis(), (oldPhi, newPhi) -> {
            // 那么 phi 的第 0 个参数必然就是从 pre-header 里来的
            final var incomingFromPreHeader = oldPhi.getIncomingValues().get(0);
            if (oldPhi == forLoop.getIndexPhi()) {
                Log.ensure(incomingFromPreHeader == forLoop.getBegin());
            }

            // phi 的第 1 个参数就必然是原来的循环体中来的
            final var valueFromOldLatch = oldPhi.getIncomingValues().get(1);
            final var incomingFromNewLatch = finalLastForBodyCloner.getOrCreate(valueFromOldLatch);

            newPhi.setIncomingCO(List.of(incomingFromPreHeader, incomingFromNewLatch));
        });

        CollectionTools.zip(oldHeader.phis(), newHeader.phis(), (oldPhi, newPhi) -> {
            // 必须拆开另一个循环, 这个修改会改动 oldPhi 的 hashCode 的值, 导致 lastForBody 里的 oldToNew 失效
            // 把原 header 里来自 pre-header 的改为来自 new-header 的 phi
            oldPhi.replaceOperandCO(0, newPhi);
        });

        final var function = oldHeader.getParent();
        final var headerPosition = function.indexOf(oldHeader);
        function.add(headerPosition, newHeader);
        unrolled.forEach(blocks -> function.addAll(headerPosition, blocks));

    }
}

class ForLoop {
    public static ForLoop tryConvertFrom(CanonicalLoop loop) {
        return tryConvertFromOpt(loop).orElse(null);
    }

    public static Optional<ForLoop> tryConvertFromOpt(CanonicalLoop loop) {
        if (loop.isRotated() || !loop.hasUniqueExit()) {
            return Optional.empty();
        }

        final var forLoop = new ForLoop(loop);

        if (forLoop.hasForCond() && forLoop.hasSmallBody() && forLoop.hasForLatch() && forLoop.hasSimpleExit()) {
            return Optional.of(forLoop);
        } else {
            return Optional.empty();
        }
    }

    public CanonicalLoop canonical() {
        return loop;
    }

    public LoopInvariantInfo getInfo() {
        return info;
    }

    public PhiInst getIndexPhi() {
        return indexPhi;
    }

    public Value getBegin() {
        return begin;
    }

    public Value getEnd() {
        return end;
    }

    public Value getStep() {
        return step;
    }

    boolean hasForCond() {
        // header 的条件必须是 BrCond
        final var terminator = loop.getHeader().getTerminator();
        if (terminator instanceof BrCondInst) {

            // 并且这个 Cond 必须得是比较
            final var cond = ((BrCondInst) terminator).getCond();
            if (cond instanceof CmpInst) {

                // 这个比较一定得是 <
                // TODO: 扩展到 <=
                final var cmp = ((CmpInst) cond);
                final var kind = cmp.getKind();
                if (kind == InstKind.ICmpLt) {

                    // 然后比较对象必须得是一个 phi, 一个循环无关量
                    final var lhs = cmp.getLHS();
                    final var rhs = cmp.getRHS();
                    if (lhs instanceof PhiInst && info.isInvariant(rhs)) {
                        this.indexPhi = ((PhiInst) lhs);
                        this.end = rhs;
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private final static int MAX_BLOCK_ALLOWED_IN_BODY = 5;
    boolean hasSmallBody() {
        return loop.getBody().size() <= MAX_BLOCK_ALLOWED_IN_BODY;
    }

    boolean hasForLatch() {
        final var latch = loop.getLatch();

        // latch 必须是一个直接跳转
        if (latch.getTerminator() instanceof BrInst) {
            Log.ensure(((BrInst) latch.getTerminator()).getNextBB() == loop.getHeader());

            for (final var inst : latch) {
                // latch 里面必须存在对索引变量的更新指令
                if (inst.getKind() == InstKind.IAdd) {
                    final var binop = (BinaryOpInst) inst;
                    final var lhs = binop.getLHS();
                    final var rhs = binop.getRHS();

                    // 形如 x = phi + step 的形式
                    if (lhs == indexPhi && info.isInvariant(rhs)) {
                        final var indexPhiOps = indexPhi.getOperands();
                        Log.ensure(indexPhiOps.size() == 2);
                        this.step = rhs;

                        // 并且 x 要作为 phi 的成员之一
                        if (indexPhiOps.contains(binop)) {
                            final var other = getOtherOne(indexPhiOps, binop);
                            // 考虑功能性样例 26, 有可能另一个也是依赖自己的
                            // // 并且 phi 的另一个成员必须是从循环外界继承而来的循环无关变量
                            // // 似乎按正规循环的要求, header 必须要有 pre-header, 所以另一个似乎必然是循环无关变量?
                            // Log.ensure(info.isInvariant(other));

                            // 并且不管怎么样都好, 理论上 begin 这种只求值一次的爱咋地咋地, 反正都可以展
                            this.begin = other;
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    boolean hasSimpleExit() {
        final var exit = loop.getUniqueExit();
        return exit.getPredecessorSize() == 1;
    }

    private <T> T getOtherOne(Collection<T> collection, T one) {
        Log.ensure(collection.size() == 2);
        final var set = new HashSet<>(collection);
        Log.ensure(set.size() == 2);

        set.remove(one);
        return set.iterator().next();
    }

    private ForLoop(CanonicalLoop loop) {
        this.loop = loop;
        this.info = LoopInvariantInfo.collect(loop);
    }

    private final CanonicalLoop loop;
    private LoopInvariantInfo info;
    private PhiInst indexPhi;
    private Value begin;
    private Value end;
    private Value step;
    // private InstKind condKind;
}

class NewHeaderCreator extends MultiBasicBlockCloner {
    public NewHeaderCreator(ForLoop loop, int totalUnrollCount) {
        super(Set.of(loop.canonical().getHeader()));

        this.loop = loop;
        this.totalUnrollCount = totalUnrollCount;
    }

    public BasicBlock create() {
        final var header = loop.canonical().getHeader();
        final var symbol = header.getSymbol().newSymbolWithSuffix("_new_header");
        final var block = BasicBlock.createFreeBBlock(symbol);

        for (final var phi : header.phis()) {
            final var newPhi = new PhiInst(phi.getType(), phi.getWaitFor());
            oldToNew.put(phi, newPhi);
            block.add(newPhi);
        }

        header.nonPhiAndTerminator().stream().map(this::getOrCreate).forEach(block::add);

        // wp(weakest per-condition): index + (totalUnrollCount - 1) * step <= end
        final var step = getOrCreate(loop.getStep());
        final var end = getOrCreate(loop.getEnd());

        final var newIndexPhi = getOrCreate(loop.getIndexPhi());
        final var offsetCount = Constant.createIntConstant(totalUnrollCount - 1);
        final var offsetInst = new BinaryOpInst(InstKind.IMul, offsetCount, step);
        final var newIndexInst = new BinaryOpInst(InstKind.IAdd, newIndexPhi, offsetInst);
        final var newCond = new CmpInst(InstKind.ICmpLt, newIndexInst, end);

        // step 跟 end 要么来自 header 的支配节点 (即外界), 不用 add
        // 要么是 header 内自己创建的, 也不用 add
        // if (step instanceof Instruction) {
        //     block.add((Instruction) step);
        // }
        //
        // if (end instanceof Instruction) {
        //     block.add((Instruction) end);
        // }

        block.add(offsetInst);
        block.add(newIndexInst);
        block.add(newCond);

        this.cond = newCond;
        this.block = block;

        block.adjustPhiEnd();
        return block;
    }

    public void setBranch(BasicBlock firstIndexBlock) {
        final var oldHeader = loop.canonical().getHeader();
        block.setBrCond(cond, firstIndexBlock, loop.canonical().getHeader());

        oldHeader.removePredecessor(block);
    }

    public ForLoop getLoop() {
        return loop;
    }

    @Override
    public <T extends Value> T getOrCreate(final T old) {
        return super.getOrCreate(old);
    }

    private final ForLoop loop;
    private final int totalUnrollCount;
    private BasicBlock block;
    private CmpInst cond;
}

class IndexBlockCreator extends MultiBasicBlockCloner {
    public static IndexBlockCreator createFromLast(ForBodyCloner lastBodyCloner) {
        final var loop = lastBodyCloner.getLoop();
        final var unrollCount = lastBodyCloner.getUnrollCount() + 1;
        final var creator = new IndexBlockCreator(loop, unrollCount);

        for (final var phi : loop.canonical().getHeader().phis()) {
            final var valueFromBody = phi.getOperands().get(1);
            final var valueAfterLastRoll = lastBodyCloner.getOrCreate(valueFromBody);

            creator.oldToNew.put(phi, valueAfterLastRoll);
        }

        return creator;
    }

    public static IndexBlockCreator createFromNewHeader(NewHeaderCreator newHeaderCreator) {
        final var loop = newHeaderCreator.getLoop();
        final var creator = new IndexBlockCreator(loop, 0);

        for (final var phi : loop.canonical().getHeader().phis()) {
            final var valueInNewHeader = newHeaderCreator.getOrCreate(phi);
            creator.oldToNew.put(phi, valueInNewHeader);
        }

        return creator;
    }

    public BasicBlock getNewIndexBlock() {
        if (newIndexBlock == null) {
            newIndexBlock = createNewIndexBlock();
        }

        return newIndexBlock;
    }

    public ForLoop getLoop() {
        return loop;
    }

    public int getUnrollCount() {
        return unrollCount;
    }

    @Override
    public <T extends Value> T getOrCreate(final T old) {
        return super.getOrCreate(old);
    }

    public Map<Value, Value> getAllReplacement() {
        return super.oldToNew;
    }

    private BasicBlock createNewIndexBlock() {
        final var header = loop.canonical().getHeader();
        final var symbol = header.getSymbol().newSymbolWithSuffix("_index_" + unrollCount);
        final var block = BasicBlock.createFreeBBlock(symbol);

        // 其实似乎 Cond 也不需要 clone ?
        // 管它呢, 反正交给 ClearUselessInstruction 了
        header.nonPhiAndTerminator().stream().map(this::getOrCreate).forEach(block::add);

        return block;
    }

    private IndexBlockCreator(ForLoop loop, int unrollCount) {
        super(Set.of(loop.canonical().getHeader()));

        this.loop = loop;
        this.unrollCount = unrollCount;
    }

    private final ForLoop loop;
    private final int unrollCount;
    private BasicBlock newIndexBlock = null;
}

class ForBodyCloner extends MultiBasicBlockCloner {
    public ForBodyCloner(IndexBlockCreator indexBlockCreator) {
        // 必须要是 getAll 才能使得 MultiBasicBlockCloner 认为来自 header 的 value 是需要替换的
        super(indexBlockCreator.getLoop().canonical().getAll());

        this.loop = indexBlockCreator.getLoop();
        this.unrollCount = indexBlockCreator.getUnrollCount();
        this.indexBlockCreator = indexBlockCreator;

        // 给一个对应用于重整各个块的前继
        // oldToNew.put(loop.canonical().getHeader(), indexBlockCreator.getNewIndexBlock());
        oldToNew.putAll(indexBlockCreator.getAllReplacement());
    }

    public Set<BasicBlock> convert() {
        // 开始复制
        final var newBBs = convert(loop.canonical().getBody());

        // 设置 indexBlock 到 body 头的跳转
        final var newBodyEntry = getNewBodyEntry();
        indexBlockCreator.getNewIndexBlock().setBr(newBodyEntry);

        // 清除 newLatch 最后的到 header 的 Br
        final var latch = getNewLatch();
        Log.ensure(latch.isTerminated());
        Log.ensure(latch.getTerminator() instanceof BrInst);
        latch.remove(latch.size() - 1);

        // 保证 indexBlockCreator 在 Set 的最开始
        final var result = new LinkedHashSet<BasicBlock>();
        result.add(indexBlockCreator.getNewIndexBlock());
        result.addAll(newBBs);
        return result;
    }

    public BasicBlock getNewLatch() {
        return getOrCreate(loop.canonical().getLatch());
    }

    public ForLoop getLoop() {
        return loop;
    }

    public int getUnrollCount() {
        return unrollCount;
    }

    @Override
    public <T extends Value> T getOrCreate(final T old) {
        return super.getOrCreate(old);
    }

    @Override
    protected BasicBlock createNewBBFromOld(final BasicBlock oldBB) {
        return BasicBlock.createFreeBBlock(oldBB.getSymbol().newSymbolWithSuffix("_unroll_" + unrollCount));
    }

    @Override
    protected BasicBlock getOtherBB(final BasicBlock blockShouldNotBeCloned) {
        return super.getOtherBB(blockShouldNotBeCloned);
    }

    private BasicBlock createIndexBlock() {
        final var symbol = loop.canonical().getHeader().getSymbol()
            .newSymbolWithName("_unroll_" + unrollCount + "_index");
        return BasicBlock.createFreeBBlock(symbol);
    }

    private BasicBlock getNewBodyEntry() {
        final var oldBodyEntry = loop.canonical().getCondInst().getTrueBB();
        return getOrCreate(oldBodyEntry);
    }

    private final ForLoop loop;
    private final int unrollCount;
    private final IndexBlockCreator indexBlockCreator;
}