package pass.ir.loop;

import ir.BasicBlock;
import ir.inst.BrCondInst;
import ir.inst.BrInst;
import utils.Log;

import java.util.*;
import java.util.stream.Collectors;

public class NaturalLoop {
    public NaturalLoop(BasicBlock header) {
        this(null, header);
    }

    public NaturalLoop(NaturalLoop parent, BasicBlock header) {
        this.header = header;
        this.parent = Optional.ofNullable(parent);
        this.subLoops = new ArrayList<>();
        this.blocks = new LinkedHashSet<>();

        this.parent.ifPresent(p -> p.subLoops.add(this));
    }

    public Optional<NaturalLoop> getParent() {
        return parent;
    }

    public List<NaturalLoop> getSubLoops() {
        return Collections.unmodifiableList(subLoops);
    }

    public BasicBlock getHeader() {
        return header;
    }

    public void setHeader(final BasicBlock header) {
        this.header = header;
    }

    public void addBlockCO(BasicBlock block) {
        blocks.add(block);
        block.getAnalysisInfo(LoopBlockInfo.class).setLoop(this);
    }

    public void removeBlockCO(BasicBlock block) {
        blocks.remove(block);
        block.getAnalysisInfo(LoopBlockInfo.class).setLoop(null);
    }

    public Set<BasicBlock> getBlocks() {
        return Collections.unmodifiableSet(blocks);
    }

    public Set<BasicBlock> getBodyBlocks() {
        final var bodyBlocks = new LinkedHashSet<>(blocks);
        bodyBlocks.remove(header);
        return bodyBlocks;
    }

    public boolean isInLoop(BasicBlock block) {
        return block == header || isInBody(block);
    }

    public boolean isInBody(BasicBlock block) {
        return blocks.contains(block);
    }

    public boolean isNotInLoop(BasicBlock block) {
        return !isInLoop(block);
    }

    public List<NaturalLoop> allLoopInPostOrder() {
        final var result = new ArrayList<NaturalLoop>();
        subLoops.stream().map(NaturalLoop::allLoopInPostOrder).forEach(result::addAll);
        result.add(this);
        return result;
    }

    public boolean isWhileLoop() {
        return header.getTerminator() instanceof BrCondInst;
    }

    public boolean isDoWhileLoop() {
        return header.getTerminator() instanceof BrInst;
    }

    /**
     * @return 所有有反向边回到 header 的块集合
     */
    public Set<BasicBlock> getLatchBlocks() {
        return header.getPredecessors().stream().filter(blocks::contains).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * @return 所有有跳转到循环外边的块的循环内基本块
     */
    public Set<BasicBlock> getExitingBlocks() {
        return blocks.stream()
            .filter(block -> block.getSuccessors().stream().anyMatch(this::isNotInLoop))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 由于 SysY 是严格结构化的语言, 所以理论上应该每个循环都有且只有一个退出块 (也就是 Cond 块对应的那个块)
     * @return 所有有跳转到循环外边的块所跳转到的那个块
     */
    public BasicBlock getExitTo() {
        if (isWhileLoop()) {
            final var exitingBlocks = getExitingBlocks();
            final var exitToBlocks = exitingBlocks.stream()
                .flatMap(block -> block.getSuccessors().stream().filter(this::isNotInLoop))
                .distinct().collect(Collectors.toList());

            Log.ensure(exitToBlocks.size() == 1, "Multi or no exit to block for a loop");

            return exitToBlocks.get(0);
        } else {
            Log.ensure(isDoWhileLoop());

            final var latchBlocks = getLatchBlocks();
            Log.ensure(latchBlocks.size() == 1, "Do-while loop must have exactly 1 latch block");
            final var latch = latchBlocks.iterator().next();

            return ((BrCondInst) latch.getTerminator()).getFalseBB();
        }
    }

    private BasicBlock header;
    // 包括子循环的 Block
    private final Set<BasicBlock> blocks;

    private final Optional<NaturalLoop> parent;

    // 顺序按照在循环体内出现的顺序
    // 子循环的子循环不计入
    private final List<NaturalLoop> subLoops;
}
