package top.origami404.ssyc.pass.ir;

import top.origami404.ssyc.ir.BasicBlock;
import top.origami404.ssyc.ir.Function;
import top.origami404.ssyc.utils.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** <h2>清扫基本块</h2>
 */
public class BlockClear {
    /** 消除函数中无法到达的块 */
    public static boolean clearUnreachableBlock(Function function) {
        // 不可以直接清除没有前继的块
        // 首先, 起始块是没有前继的
        // 其次, 有些不可达的循环有可能自引用, 导致删不掉
        // 所以要来一趟 DFS
        final var reachableBB = (new BasicBlockDFS()).collectReachableBB(function.getEntryBBlock());

        boolean hasChanged = false;

        for (final var block : function.getBasicBlocks()) {
            if (!reachableBB.contains(block)) {
                for (final var succ : block.getSuccessors()) {
                    succ.removePredecessorWithPhiUpdated(block);
                }

                function.getIList().remove(block);
                hasChanged = true;
            }
        }

        return hasChanged;
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

    /** 合并两个紧密相连的块 (若 A -> B, A 的后继只有 B, B 的前继只有 A, 则它们是紧密相联的) */
    public static boolean mergeBlock(Function function) {
        final var oldBlocks = new ArrayList<>(function.getBasicBlocks());

        for (final var block : oldBlocks) {
            final var isUniqueSucc = block.getSuccessors().size() == 1;
            if (!isUniqueSucc) {
                continue;
            }

            final var succ = block.getSuccessors().get(0);
            final var isUniquePred = succ.getPredecessors().size() == 1;
            if (!isUniquePred) {
                continue;
            }

            // block -> succ
            Log.ensure(succ.getPredecessors().get(0) == block, "A -> B, B <- A");
            // 将 succ 的内容合并到 block 中去
            // succ 不可能有 phi. 因为若 succ 有 phi, 那么该 phi 必然都是一个参数的, 而这意味着它会被消除
            Log.ensure(succ.phis().size() == 0, "Block with unique predecessor should NOT have any phi");
            // 然后删除 block 的跳转, 再把 succ 的全部指令加进去 (这一步之后 succ 的跳转就是 block 的跳转了)
            block.getIList().remove(block.getTerminator());
            succ.nonPhis().forEach(block.getIList()::add);
            // 然后把以 succ 为前继的所有块都换成 block
            succ.replaceAllUseWith(block);
            // 最后删除 succ
            function.getIList().remove(succ);
            // 基本块的后继是通过访问跳转指令获取的, 所以不需要额外维护
            return true;
        }

        return false;
    }
}
