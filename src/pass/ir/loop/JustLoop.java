package pass.ir.loop;

import ir.BasicBlock;

import java.util.*;
import java.util.stream.Collectors;

public class JustLoop {
    JustLoop(JustLoop parent, BasicBlock header) {
        this.header = header;
        this.body = new LinkedHashSet<>();

        this.parent = Optional.ofNullable(parent);
        this.subLoops = new ArrayList<>();
    }

    public Set<BasicBlock> getAll() {
        final var set = new LinkedHashSet<>(body);
        set.add(header);
        return set;
    }

    public LinkedHashSet<BasicBlock> getBody() {
        return body;
    }

    public BasicBlock getHeader() {
        return header;
    }

    public Optional<JustLoop> getParent() {
        return parent;
    }

    public void setParent(final JustLoop parent) {
        this.parent = Optional.ofNullable(parent);
    }

    public List<JustLoop> getSubLoops() {
        return subLoops;
    }

    /**
     * 顶层循环是 1, 不在循环中的块是 0
     */
    public int getLoopDepth() {
        return parent.map(JustLoop::getLoopDepth).map(i -> i + 1).orElse(1);
    }

    public List<JustLoop> allSubLoopsInPostOrder() {
        final var children = subLoops.stream()
            .map(JustLoop::allSubLoopsInPostOrder)
            .flatMap(List::stream).collect(Collectors.toList());

        children.add(this);
        return children;
    }

    public static List<JustLoop> allLoopsInPostOrder(List<JustLoop> loops) {
        return loops.stream().map(JustLoop::allSubLoopsInPostOrder).flatMap(List::stream).collect(Collectors.toList());
    }

    final BasicBlock header;
    final LinkedHashSet<BasicBlock> body;

    Optional<JustLoop> parent;
    final List<JustLoop> subLoops;
}
