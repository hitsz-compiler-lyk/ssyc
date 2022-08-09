package pass.ir.dataflow;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import ir.BasicBlock;
import ir.Function;
import ir.Module;
import pass.ir.IRPass;

public abstract class ForwardDataFlowPass<T, I extends DataFlowInfo<T>> implements IRPass {
    protected abstract T transfer(BasicBlock block, T in);
    protected abstract T meet(BasicBlock block, List<T> predOuts);

    protected abstract T topElement(BasicBlock block);
    protected abstract T entryIn(BasicBlock block);

    protected abstract Class<I> getInfoClass();
    protected abstract I createInfo(BasicBlock block);

    @Override
    public void runPass(Module module) {
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
        final var entry = function.getEntryBBlock();
        final var nonEntries = nonEntries(function);

        entry.addAnalysisInfo(createEntryInfo(entry));
        for (final var block : nonEntries) {
            block.addAnalysisInfo(createEmptyInfo(block));
        }
    }

    public void iterUntilFixPoint(Function function) {
        boolean hasChanged = true;
        while (hasChanged) {
            hasChanged = false;

            for (final var block : function) {
                final var info = getInfo(block);

                final var predOuts = block.getPredecessors().stream()
                    .map(this::getInfo).map(DataFlowInfo::out).collect(Collectors.toList());
                final var newIn = meet(block, predOuts);

                if (!newIn.equals(info.in)) {
                    hasChanged = true;
                    info.in = newIn;
                    info.out = transfer(block, newIn);
                }
            }
        }
    }

    private I createEmptyInfo(BasicBlock block) {
        final var info = createInfo(block);
        info.in = topElement(block);
        info.out = topElement(block);
        info.needUpdate = false;
        return info;
    }

    private I createEntryInfo(BasicBlock block) {
        final var info = createInfo(block);
        info.in = entryIn(block);
        info.out = transfer(block, info.in);
        info.needUpdate = true;
        return info;
    }

    private I getInfo(BasicBlock block) {
        return block.getAnalysisInfo(getInfoClass());
    }

    private List<BasicBlock> nonEntries(Function function) {
        final var list = new LinkedList<>(function);
        list.remove(0);
        return list;
    }
}
