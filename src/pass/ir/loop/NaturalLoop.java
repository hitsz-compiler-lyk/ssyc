package pass.ir.loop;

import ir.BasicBlock;

import java.util.*;

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

    public Set<BasicBlock> getBlocks() {
        return Collections.unmodifiableSet(blocks);
    }

    public Set<BasicBlock> getBodyBlocks() {
        final var bodyBlocks = new LinkedHashSet<>(blocks);
        bodyBlocks.remove(header);
        return bodyBlocks;
    }

    public boolean containsBlock(BasicBlock block) {
        return blocks.contains(block);
    }

    public List<NaturalLoop> allLoopInPostOrder() {
        final var result = new ArrayList<NaturalLoop>();
        subLoops.stream().map(NaturalLoop::allLoopInPostOrder).forEach(result::addAll);
        result.add(this);
        return result;
    }

    private BasicBlock header;
    // 包括子循环的 Block
    private final Set<BasicBlock> blocks;

    private final Optional<NaturalLoop> parent;

    // 顺序按照在循环体内出现的顺序
    // 子循环的子循环不计入
    private final List<NaturalLoop> subLoops;
}
