package pass.ir.dataflow;

import ir.BasicBlock;
import ir.Function;
import ir.Module;
import pass.ir.IRPass;

import java.util.List;
import java.util.stream.Collectors;

public abstract class BackwardDataFlowPass<T, I extends DataFlowInfo<T>> implements IRPass {
    protected abstract T transfer(BasicBlock block, T out);
    protected abstract T meet(BasicBlock block, List<T> succIns);

    protected abstract T topElement(BasicBlock block);
    protected abstract T tailOut(BasicBlock tail);

    protected abstract Class<I> getInfoClass();
    protected abstract I createInfo(BasicBlock block);

    @Override
    public void runPass(final Module module) {
        module.getNonExternalFunction().forEach(this::runOnFunction);
    }

    public void runOnFunction(Function func) {
        clearInfo(func);
        insertInfo(func);
        iterUntilFixPoint(func);
    }

    public void clearInfo(Function function) {
        final var cls = getInfoClass();
        for (final var block : function) {
            if (block.containsAnalysisInfo(cls)) {
                block.removeAnalysisInfo(cls);
            }
        }
    }

    public void insertInfo(Function function) {
        for (final var block : function) {
            if (block.getSuccessors().size() == 0) {
                block.addAnalysisInfo(createTailInfo(block));
            } else {
                block.addAnalysisInfo(createEmptyInfo(block));
            }
        }
    }

    public void iterUntilFixPoint(Function function) {
        boolean hasChanged = true;
        while (hasChanged) {
            hasChanged = false;

            for (final var block : function) {
                final var info = getInfo(block);

                final var succIns = block.getSuccessors().stream()
                    .map(this::getInfo).map(DataFlowInfo::in).collect(Collectors.toList());
                final var newOut = meet(block, succIns);

                if (!newOut.equals(info.out)) {
                    hasChanged = true;
                    info.out = newOut;
                    info.in = transfer(block, newOut);
                }
            }
        }
    }

    private I createEmptyInfo(BasicBlock block) {
        final var info = createInfo(block);
        info.out = topElement(block);
        info.in = topElement(block);
        info.needUpdate = false;
        return info;
    }

    private I createTailInfo(BasicBlock block) {
        final var info = createInfo(block);
        info.out = tailOut(block);
        info.in = transfer(block, info.out);
        info.needUpdate = true;
        return info;
    }

    private I getInfo(BasicBlock block) {
        return block.getAnalysisInfo(getInfoClass());
    }
}
