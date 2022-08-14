package pass.ir.loop;

import ir.BasicBlock;
import ir.Function;
import pass.ir.ConstructDominatorInfo.DominatorInfo;
import utils.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CollectLoops {
    public static List<JustLoop> all(Function function) {
        final var collector = new CollectLoops();
        return collector.collectAllLoops(function);
    }

    public static List<JustLoop> allAndaddToBlockInfo(Function function) {
        for (final var block : function) {
            if (block.containsAnalysisInfo(JustLoopBlockInfo.class)) {
                block.removeAnalysisInfo(JustLoopBlockInfo.class);
            }

            block.addAnalysisInfo(new JustLoopBlockInfo());
        }

        final var allLoops = JustLoop.allLoopsInPostOrder(topLevel(function));
        for (final var loop : allLoops) {
            for (final var block : loop.getAll()) {
                final var info = block.getAnalysisInfo(JustLoopBlockInfo.class);
                info.setLoop(loop);
            }
        }

        return allLoops;
    }

    public static List<JustLoop> topLevel(Function function) {
        final var collector = new CollectLoops();
        return collector.collectTopLevelLoops(function);
    }

    private List<JustLoop> collectTopLevelLoops(Function function) {
        return collectAllLoops(function).stream().filter(loop -> loop.getParent().isEmpty()).collect(Collectors.toList());
    }

    private JustLoop currLoop = null;
    private List<JustLoop> collectAllLoops(Function function) {
        final var loops = new ArrayList<JustLoop>();

        for (final var block : function) {
            // 理论上基本块都是按顺序排列的
            // 那么当我们第一次碰到不在当前循环里的块的时候, 我们就离开了这个循环了
            while (currLoop != null && !currLoop.body.contains(block)) {
                currLoop = currLoop.parent.orElse(null);
            }
            // 确保弹出一次 currLoop 之后, 当前块在 currLoop 里
            Log.ensure(currLoop == null || currLoop.body.contains(block));

            final var domChildrenInPred = block.getPredecessors().stream()
                .filter(pred -> DominatorInfo.dom(pred).contains(block)).collect(Collectors.toList());

            if (!domChildrenInPred.isEmpty()) {
                currLoop = new JustLoop(currLoop, block);
                domChildrenInPred.forEach(this::collectBlocksInLoop);

                loops.add(currLoop);
                currLoop.parent.ifPresent(parent -> {
                    // 有时候会有触及不到的情况
                    // 考虑功能性样例 55, CFG 如下的情况:
                    // ^> A
                    // |  v
                    // C< B <-> D
                    // BD 组成的循环相当于 "外挂" 在外层循环 ABC 上, 这时候做前继闭包是做不到 D 的
                    parent.body.addAll(currLoop.body);
                    parent.subLoops.add(currLoop);
                });
            }
        }

        return loops;
    }

    private void collectBlocksInLoop(BasicBlock block) {
        if (block == currLoop.header) {
            return;
        }

        currLoop.body.add(block);

        block.getPredecessors().stream()
            // 防止无限循环或超出循环范围
            .filter(pred -> !currLoop.body.contains(pred) && pred != currLoop.header)
            // 理论上基本块都是按顺序排列的
            // 一个块如果在一个内层循环里, 那它必然会先加入到外层循环,再加入到内层循环中
            // 那么最后这个块就会在最内层循环里了
            .forEach(this::collectBlocksInLoop);
    }
}
