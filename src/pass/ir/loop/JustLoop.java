package pass.ir.loop;

import ir.BasicBlock;

import java.util.*;

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

    public Set<BasicBlock> getBody() {
        return body;
    }

    public BasicBlock getHeader() {
        return header;
    }

    public Optional<JustLoop> getParent() {
        return parent;
    }

    public List<JustLoop> getSubLoops() {
        return subLoops;
    }

    BasicBlock header;
    Set<BasicBlock> body;

    Optional<JustLoop> parent;
    List<JustLoop> subLoops;
}
