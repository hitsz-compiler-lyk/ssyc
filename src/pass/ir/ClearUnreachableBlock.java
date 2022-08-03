package pass.ir;

import ir.BasicBlock;
import ir.Function;
import ir.Module;

import java.util.HashSet;
import java.util.Set;

/** 消除函数中无法到达的块 */
public class ClearUnreachableBlock implements IRPass {
    @Override
    public void runPass(final Module module) {
        module.getNonExternalFunction().forEach(ClearUnreachableBlock::run);
    }

    public static void run(Function func) {
        // 不可以直接清除没有前继的块
        // 首先, 起始块是没有前继的
        // 其次, 有些不可达的循环有可能自引用, 导致删不掉
        // 所以要来一趟 DFS
        final var reachableBB = (new BasicBlockDFS()).collectReachableBB(func.getEntryBBlock());

        for (final var block : func) {
            if (!reachableBB.contains(block)) {
                for (final var succ : block.getSuccessors()) {
                    succ.removePredecessorWithPhiUpdated(block);
                }

                // 因为控制流流不到的地方有可能有循环
                // 这样有些块就会一直有前继 (operand)
                // 所以必须调用不检查的方法
                // 直接现在就 free 掉有可能会影响上面的删前继 (这时候会导致上面的 remove 方法检查不通过)
                // 所以删完下面再 free
                // block.freeAllWithoutCheck();
            }
        }

        IRPass.copyForChange(func).stream()
            .filter(b -> !reachableBB.contains(b))
            .forEach(BasicBlock::freeAllWithoutCheck);
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
