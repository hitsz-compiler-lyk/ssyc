package pass.ir.loop;

import ir.BasicBlock;
import ir.Function;
import pass.ir.ConstructDominatorInfo;
import pass.ir.ConstructDominatorInfo.DominatorInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CollectLoops {
    public static List<JustLoop> all(Function function) {
        final var collector = new CollectLoops();
        return collector.runOnFunction(function);
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
        return runOnFunction(function).stream().filter(loop -> loop.getParent().isEmpty()).collect(Collectors.toList());
    }

    private List<JustLoop> runOnFunction(Function function) {
        final var domConstructor = new ConstructDominatorInfo();
        domConstructor.runOnFunction(function);
        return collectAllLoops(function);
    }

    private List<JustLoop> collectAllLoops(Function function) {
        final var loops = new ArrayList<JustLoop>();

        for (final var block : function) {
            // 理论上基本块都是按顺序排列的
            // 但实际上不是, 所以我们需要一个与基本块排列顺序无关的方法来识别循环

            final var domChildrenInPred = block.getPredecessors().stream()
                .filter(pred -> DominatorInfo.dom(pred).contains(block)).collect(Collectors.toList());

            if (!domChildrenInPred.isEmpty()) {
                final var loop = new JustLoop(null, block);
                domChildrenInPred.forEach(pred -> collectBlocksInLoop(loop, pred));
                loops.add(loop);
            }
        }

        for (final var loop : loops) {
            for (final var possibleParentLoop : loops) {
                if (!isAncestor(possibleParentLoop, loop)) {
                    continue;
                }

                // 有时候直接做前继闭包会有触及不到的情况
                // 考虑功能性样例 55, CFG 如下的情况:
                // ^> A
                // |  v
                // C< B <-> D
                // BD 组成的循环相当于 "外挂" 在外层循环 ABC 上, 这时候做前继闭包是做不到 D 的, 需要特别对父循环再加入子循环
                possibleParentLoop.body.addAll(loop.body);
            }
        }

        for (final var loop : loops) {
            int currMinParentSize = Integer.MAX_VALUE;
            JustLoop currDirectParent = null;

            for (final var possibleParentLoop : loops) {
                if (!isAncestor(possibleParentLoop, loop)) {
                    continue;
                }

                final var bodySize = possibleParentLoop.body.size();
                if (bodySize < currMinParentSize) {
                    currMinParentSize = bodySize;
                    currDirectParent = possibleParentLoop;
                }
            }

            loop.setParent(currDirectParent);
        }

        return loops;
    }

    private boolean isAncestor(JustLoop possibleSon, JustLoop possibleAncestor) {
        return possibleAncestor.body.contains(possibleSon.header);
    }

    private void collectBlocksInLoop(JustLoop loop, BasicBlock block) {
        if (block == loop.header) {
            return;
        }

        loop.body.add(block);

        block.getPredecessors().stream()
            // 防止无限循环或超出循环范围
            .filter(pred -> !loop.body.contains(pred) && pred != loop.header)
            // 理论上基本块都是按顺序排列的
            // 一个块如果在一个内层循环里, 那它必然会先加入到外层循环,再加入到内层循环中
            // 那么最后这个块就会在最内层循环里了
            .forEach(pred -> collectBlocksInLoop(loop, pred));
    }
}
