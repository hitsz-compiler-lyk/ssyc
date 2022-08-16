package pass.ir;

import ir.Function;
import ir.GlobalModificationStatus;
import ir.Module;
import ir.Value;
import ir.inst.PhiInst;

import java.util.HashSet;

public class RemoveTrivialPhi implements IRPass {
    @Override
    public void runPass(final Module module) {
        GlobalModificationStatus.doUntilNoChange(() ->
            module.getNonExternalFunction().forEach(this::removeTrivialPhi));
    }

    public void removeTrivialPhi(Function function) {
        for (final var block : function) {
            block.phis().forEach(this::tryRemovePhi);
            block.adjustPhiEnd();
        }
    }

    public void tryRemovePhi(PhiInst phi) {
        final var replacement = tryFindPhiReplacement(phi);
        if (replacement != phi) {
            // 如果能去掉
            // 首先删除原 phi 所有 incoming (会去除所有 user)
            phi.clearIncomingCO();
            // 然后将其所有出现都替换掉
            phi.replaceAllUseWith(replacement);
            // 然后将其从块中删除
            phi.freeAll();
        }
    }

    public Value tryFindPhiReplacement(PhiInst phi) {
        // incoming 去重
        final var incoming = new HashSet<>(phi.getIncomingValues());
        // 这里的 phi 不能删除自己, 因为此时的 phi 如果还有到自己的引用, 那必然是直接循环导致的
        // ? 为什么直接循环导致的到自己的引用不能删来着 ?
        incoming.remove(phi);

        final var isDeadBlock = phi.getParentOpt().orElseThrow().getPredecessors().isEmpty();
        if (incoming.isEmpty() && !isDeadBlock) {
            // 暂时让死代码中的 phi 可以有零个参数
            throw new RuntimeException("Phi for undefined: " + phi);
        } else if (incoming.size() == 1) {
            // 如果去重后只有一个, 那么这个 Phi 是可以去掉的
            return incoming.iterator().next();
        } else {
            // 否则, 这个 phi 不可以去掉
            return phi;
        }
    }
}
