package pass.ir.loop;

import ir.inst.CallInst;
import ir.inst.Instruction;
import ir.inst.PhiInst;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

public class SimpleInvariantHoist implements LoopPass {
    @Override
    public void runOnLoop(final CanonicalLoop loop) {
        final var info = LoopInvariantInfo.collect(loop);

        final var blocksOnlyBelongToThisLoop = new LinkedHashSet<>(loop.getBody());
        loop.getSubLoops().stream().map(CanonicalLoop::getAll).forEach(blocksOnlyBelongToThisLoop::removeAll);

        final var invariants = blocksOnlyBelongToThisLoop.stream()
            .flatMap(List::stream)
            .filter(info::isInvariant)
            .filter(inst -> !(inst instanceof PhiInst))
            .filter(inst -> !(inst instanceof CallInst))
            .collect(Collectors.toSet());


        final var preHeader = loop.getPreHeader();
        // 因为归纳变量消除是把归纳变量初始值放到 preHeader 的末尾的
        // 所以如果 gep 类型的归纳变量的不变的索引使用到块内现算的值的话,
        // 这些现算的值需要放到 preHeader 头部, 才能保证支配归纳变量的 gep
        // 详见性能样例 sl1
        // 同时还要保证这些提升上来的表达式之间本身就保持着拓扑顺序
        final var invariantInOrder = new TopoSort(invariants).getInstructionsInTopoOrder();
        invariantInOrder.forEach(preHeader::addInstBeforeTerminator);
        preHeader.adjustPhiEnd();
    }
}

class TopoSort {
    public TopoSort(Collection<Instruction> instructions) {
        this.instructions = new LinkedHashSet<>(instructions);
        this.order = new LinkedHashSet<>();
    }

    public List<Instruction> getInstructionsInTopoOrder() {
        if (order.isEmpty()) {
            instructions.forEach(this::sortByUseDef);
        }

        return new ArrayList<>(order);
    }

    void sortByUseDef(Instruction curr) {
        if (order.contains(curr)) {
            return;
        }

        for (final var op : curr.getOperands()) {
            if (op instanceof final Instruction opInst) {
                if (instructions.contains(opInst)) {
                    // 所有参数都应该出现在自己的前面
                    sortByUseDef(opInst);
                }
            }
        }

        order.add(curr);
    }


    private final LinkedHashSet<Instruction> order;
    private final LinkedHashSet<Instruction> instructions;
}
