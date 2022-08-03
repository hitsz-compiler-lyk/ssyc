package pass.ir;

import ir.Function;
import ir.GlobalModifitationStatus;
import ir.Module;
import utils.Log;

public class MergeDirectBranch implements IRPass {
    @Override
    public void runPass(final Module module) {
        GlobalModifitationStatus.doUntilNoChange(() ->
            module.getNonExternalFunction().forEach(this::mergeBlock));
    }

    /** 合并两个紧密相连的块 (若 A -> B, A 的后继只有 B, B 的前继只有 A, 则它们是紧密相联的) */
    public void mergeBlock(Function function) {
        for (final var block : function) {
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
            Log.ensure(succ.phis().isEmpty(), "Block with unique predecessor should NOT have any phi");
            // 然后删除 block 的跳转, 再把 succ 的全部指令加进去 (这一步之后 succ 的跳转就是 block 的跳转了)
            block.getTerminator().freeAll();
            block.addAll(succ.nonPhis());
            // 然后把以 succ 为前继的所有块都换成 block
            succ.replaceAllUseWith(block);
            // 最后删除 succ
            succ.freeAll();
            // 基本块的后继是通过访问跳转指令获取的, 所以不需要额外维护
        }
    }
}
