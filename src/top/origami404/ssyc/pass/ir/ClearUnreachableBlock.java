package top.origami404.ssyc.pass.ir;

import top.origami404.ssyc.ir.BasicBlock;
import top.origami404.ssyc.ir.Function;
import top.origami404.ssyc.ir.Module;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** 消除函数中无法到达的块 */
public class ClearUnreachableBlock implements IRPass {
    @Override
    public void runPass(final Module module) {
        module.getNonExternalFunction().forEach(ClearUnreachableBlock::run);
    }

    public static void run(Function function) {
        // 不可以直接清除没有前继的块
        // 首先, 起始块是没有前继的
        // 其次, 有些不可达的循环有可能自引用, 导致删不掉
        // 所以要来一趟 DFS
        final var reachableBB = (new BasicBlockDFS()).collectReachableBB(function.getEntryBBlock());

        final var oldBB = new ArrayList<>(function.getBasicBlocks());
        for (final var block : oldBB) {
            if (!reachableBB.contains(block)) {
                for (final var succ : block.getSuccessors()) {
                    succ.removePredecessorWithPhiUpdated(block);
                }

                function.getIList().remove(block);
            }
        }
    }

    static class BasicBlockDFS {
        void dfs(BasicBlock nowBB) {
            visited.add(nowBB);
            for (final var succ : nowBB.getSuccessors()) {
                // 防止成环
                if (!visited.contains(succ)) {
                    dfs(succ);
                }
            }
        }

        public Set<BasicBlock> collectReachableBB(BasicBlock entry) {
            dfs(entry);
            return visited;
        }

        private final Set<BasicBlock> visited = new HashSet<>();
    }
}
