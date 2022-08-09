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
                terminator.replaceOperandCO(succ, next);
                next.replacePredecessor(succ, block);

                succ.freeAll();
            }
        }
    }
}
