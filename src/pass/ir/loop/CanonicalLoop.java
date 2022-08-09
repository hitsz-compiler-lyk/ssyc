package pass.ir.loop;

import ir.BasicBlock;
import ir.inst.BrCondInst;
import ir.inst.BrInst;
import utils.Log;

import java.util.*;
import java.util.stream.Collectors;


public class CanonicalLoop {


    // ============================ 获得各种块 =================================//
    public BasicBlock getPreHeader() {
        return header.getPredecessors().get(0);
    }

    public BasicBlock getHeader() {
        return header;
    }

    public BasicBlock getLatch() {
        return header.getPredecessors().get(1);
    }

    public BasicBlock getUniqueExit() {
        return getCondInst().getFalseBB();
    }

    public BasicBlock getCondBlock() {
        return isRotated ? getLatch() : getHeader();
    }

    public BrCondInst getCondInst() {
        return (BrCondInst) getCondBlock().getTerminator();
    }

    // 下面两个块仅限 rotated 的 loop
    public BasicBlock getGuard() {
        Log.ensure(isRotated, "Only rotated loop could have a guard block");
        return getPreHeader().getPredecessors().get(0);
    }

    public BasicBlock getFinalExit() {
        Log.ensure(isRotated, "Only rotated loop could have a final exit block");
        // or can also use guard.getFalseBB
        return getUniqueExit().getSuccessors().get(0);
    }


    // ============================ 循环森林相关方法 =================================//
    public Optional<CanonicalLoop> getParent() {
        return parent;
    }

    public List<CanonicalLoop> getSubLoops() {
        return Collections.unmodifiableList(subLoops);
    }

    public void addSubLoop(CanonicalLoop subLoop) {
        subLoops.add(subLoop);
    }

    public List<CanonicalLoop> getAllLoopInPostOrder() {
        final var result = new ArrayList<CanonicalLoop>();
        subLoops.stream().map(CanonicalLoop::getAllLoopInPostOrder).forEach(result::addAll);
        result.add(this);
        return result;
    }

    public static List<CanonicalLoop> getAllLoopInPostOrder(List<CanonicalLoop> loops) {
        return loops.stream()
            .map(CanonicalLoop::getAllLoopInPostOrder)
            .flatMap(List::stream).collect(Collectors.toList());
    }

    public boolean isTopLevelLoop() {
        return parent.isPresent();
    }

    // ============================ 判定性方法 =================================//
    public boolean isInBody(BasicBlock block) {
        return body.contains(block);
    }

    public boolean isNotInBody(BasicBlock block) {
        return !isInBody(block);
    }

    public boolean isInLoop(BasicBlock block) {
        return header == block || isInBody(block);
    }

    public boolean isRotated() {
        return isRotated;
    }


    // ============================ 块的修改性方法 =================================//
    public void markAsRotated() {
        this.isRotated = true;
    }

    public void setHeader(BasicBlock header) {
        this.header = header;
    }

    public void addBodyBlock(BasicBlock bodyBlock) {
        this.body.add(bodyBlock);
    }


    // ============================ 验证性方法与实现 =================================//
    private boolean isRotated;

    private BasicBlock header;
    private final Set<BasicBlock> body;

    private final Optional<CanonicalLoop> parent;
    private final List<CanonicalLoop> subLoops;

    public CanonicalLoop(CanonicalLoop parent, BasicBlock header) {
        this.parent = Optional.ofNullable(parent);
        this.subLoops = new ArrayList<>();

        this.header = header;
        this.body = new LinkedHashSet<>();
        this.isRotated = false;
    }

    public void verify() {
        ensure(header.getPredecessors().size() == 2, "Header of a loop must have exactly 2 pred");

        final var preHeaderCandidates = header.getPredecessors().stream()
            .filter(this::isNotInBody).collect(Collectors.toList());
        ensure(preHeaderCandidates.size() == 1, "A loop must have exactly 1 pre-header");

        final var latchCandidates = header.getPredecessors().stream()
            .filter(this::isInBody).collect(Collectors.toList());
        ensure(latchCandidates.size() == 1, "A loop must have exactly 1 latch");

        final var preHeader = preHeaderCandidates.get(0);
        final var latch = latchCandidates.get(0);

        ensure(preHeader == header.getPredecessors().get(0), "Pre-header must the 1st pred of header");
        ensure(latch == header.getPredecessors().get(1), "Latch must the 2nd pred of header");

        if (isRotated) {
            ensure(latch.getTerminator() instanceof BrCondInst, "A rotated loop must have BrCond in Latch");
        } else {
            ensure(header.getTerminator() instanceof BrCondInst, "A normal loop must have BrCond in header");
        }

        final var condInst = getCondInst();
        if (isRotated) {
            ensure(condInst.getTrueBB() == header, "TrueBB of cond in rotated loop must be header");
        } else {
            ensure(isInBody(condInst.getTrueBB()), "TrueBB of cond in normal loop must be in the body");
        }

        final var exit = condInst.getFalseBB();
        body.stream()
            .filter(block -> block != latch) // latch 也算 body 的一部分... 吗?
            .map(BasicBlock::getSuccessors)
            .flatMap(List::stream)
            .forEach(succInBody -> {
                ensure(isInBody(succInBody) || succInBody == exit,
                    "Successor of non-latch blocks in loop body must either be exit or be in the body");
            });

        if (isRotated) {
            final var guardCandidates = preHeader.getPredecessors();
            ensure(guardCandidates.size() == 1, "A rotated loop must have exactly 1 guard block");
            final var guard = guardCandidates.get(0);

            ensure(guard.getTerminator() instanceof BrCondInst, "A guard must have BrCond");
            final var guardCond = (BrCondInst) guard.getTerminator();

            ensure(guardCond.getTrueBB() == preHeader, "TrueBB of a guard must be pre-header");
            final var finalExit = guardCond.getFalseBB();

            ensure(exit.getTerminator() instanceof BrInst, "A rotated loop must have Br in exit");
            ensure(((BrInst) exit.getTerminator()).getNextBB() == finalExit,
                "Exit in rotated loop must br to final exit");
        }
    }

    private void ensure(boolean cond, String message) {
        if (!cond) {
            throw new LoopVerifyException(message);
        }
    }

    private void ensureNot(boolean cond, String message) {
        if (cond) {
            throw new LoopVerifyException(message);
        }
    }
}

class LoopVerifyException extends RuntimeException {
    LoopVerifyException(String message) {
        super(message);
    }
}
