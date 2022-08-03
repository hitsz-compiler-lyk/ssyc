package pass.ir.memory;

import ir.BasicBlock;
import ir.inst.CallInst;
import ir.inst.MemInitInst;
import ir.inst.StoreInst;
import pass.ir.dataflow.ForwardDataFlowPass;
import utils.Log;

import java.util.List;

class CollectMemoryDefination extends ForwardDataFlowPass<MemCache, MemoryInfo> {
    @Override
    protected MemCache transfer(BasicBlock block, MemCache in) {
        final var current = MemCache.copyFrom(in);

        for (final var inst : block) {
            if (inst instanceof MemInitInst) {
                current.setByInit((MemInitInst) inst);
            } else if (inst instanceof StoreInst) {
                current.setByStore((StoreInst) inst);
            } else if (inst instanceof CallInst) {
                current.setByCall((CallInst) inst);
            }
        }

        return current;
    }

    @Override
    protected MemCache meet(BasicBlock block, List<MemCache> predOuts) {
        Log.ensure(predOuts.isEmpty() == (block == block.getParent().getEntryBBlock()),
            "Only entry block could have no pred");

        return predOuts.stream().reduce(MemCache::merge).orElse(MemCache.empty());
    }

    @Override
    protected MemCache topElement(BasicBlock block) {
        return MemCache.empty();
    }

    @Override
    protected MemCache entryIn(BasicBlock block) {
        return MemCache.empty();
    }

    @Override
    protected MemoryInfo createInfo(BasicBlock block) {
        return new MemoryInfo();
    }

    @Override
    protected Class<MemoryInfo> getInfoClass() {
        return MemoryInfo.class;
    }
}
