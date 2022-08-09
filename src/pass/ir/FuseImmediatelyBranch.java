package pass.ir;

import ir.BasicBlock;
import ir.Module;
import ir.inst.BrInst;

import java.util.List;

public class FuseImmediatelyBranch implements IRPass {
    @Override
    public void runPass(final Module module) {
        module.getNonExternalFunction().stream()
            .flatMap(List::stream)
            .forEach(this::runOnBlock);
    }

    void runOnBlock(BasicBlock block) {
        final var terminator = block.getTerminator();

        for (final var succ : IRPass.copyForChange(block.getSuccessors())) {
            if (succ.size() == 1 && succ.getPredecessorSize() == 1 && succ.getTerminator() instanceof BrInst) {
                final var next = ((BrInst) succ.getTerminator()).getNextBB();

                // 如果 A -> B -> C 且 A -> C, 那么说明这个控制流会起到一个条件选择的作用
                // 这时候就不能直接合并它, 因为有可能在 C 开头会有 phi 借助控制流来求一个运行时根据条件才能确定的值
                if (next.getPredecessors().contains(block)) {
                    continue;
                }

                terminator.replaceOperandCO(succ, next);
                next.replacePredecessor(succ, block);

                succ.freeAll();
            }
        }
    }
}
