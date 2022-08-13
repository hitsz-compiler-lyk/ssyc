package pass.ir.memory;

import ir.BasicBlock;
import ir.GlobalVar;
import ir.inst.CallInst;
import ir.inst.MemInitInst;
import ir.inst.StoreInst;
import pass.ir.dataflow.ForwardDataFlowPass;
import utils.Log;

import java.util.List;
import java.util.Set;

/**
 * 收集对特定内存位置的写入
 */
class CollectMemoryDefinition extends ForwardDataFlowPass<MemCache, MemoryInfo> {
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
        final var isEntry = block == block.getParent().getEntryBBlock();
        Log.ensure(predOuts.isEmpty() == isEntry,
            "Only entry block could have no pred");

        if (isEntry) {
            // 对于函数的起始块而言, 其交汇结果应该是其 in()
            // 对于 main 函数, in 是所有全局变量的初始值
            // 对于其它函数, in 是空集
            return block.getAnalysisInfo(MemoryInfo.class).in();
        }

        return predOuts.stream().reduce(MemCache::merge).orElse(MemCache.empty());
    }

    @Override
    protected MemCache topElement(BasicBlock block) {
        return MemCache.empty();
    }

    @Override
    protected MemCache entryIn(BasicBlock block) {
        final var function = block.getParent();

        // 只有 main 函数开头才能保证全局变量的值是初始值本身
        if (function.getFunctionSourceName().equals("main")) {
            final var cache = MemCache.empty();
            globalVars.forEach(cache::setByGlobalVar);
            return cache;

        } else {
            return MemCache.empty();
        }
    }

    @Override
    protected MemoryInfo createInfo(BasicBlock block) {
        return new MemoryInfo();
    }

    @Override
    protected Class<MemoryInfo> getInfoClass() {
        return MemoryInfo.class;
    }

    CollectMemoryDefinition(Set<GlobalVar> globalVars) {
        this.globalVars = globalVars;
    }
    private final Set<GlobalVar> globalVars;
}
