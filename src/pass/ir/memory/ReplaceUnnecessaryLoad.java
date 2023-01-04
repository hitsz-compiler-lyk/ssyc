package pass.ir.memory;

import ir.Function;
import ir.Module;
import ir.inst.CallInst;
import ir.inst.LoadInst;
import ir.inst.MemInitInst;
import ir.inst.StoreInst;
import pass.ir.IRPass;

// TODO_: 缓存相同变量的内存位置: 不做了, 感觉提升不大

/**
 * 将对已知值的内存位置的访问替换为对应的已知值
 */
public class ReplaceUnnecessaryLoad implements IRPass {
    @Override
    public void runPass(Module module) {
        final var collector = new CollectMemoryDefinition(module.getVariables());
        collector.runPass(module);
        module.getNonExternalFunction().forEach(this::runOnFunction);
    }

    void runOnFunction(Function function) {
        for (final var block : function) {
            final var current = block.getAnalysisInfo(MemoryInfo.class).in();

            for (final var inst : block) {
                // Def
                if (inst instanceof MemInitInst) {
                    current.setByInit((MemInitInst) inst);
                } else if (inst instanceof StoreInst) {
                    current.setByStore((StoreInst) inst);
                } else if (inst instanceof CallInst) {
                    current.setByCall((CallInst) inst);
                }
                // Use
                else if (inst instanceof final LoadInst load) {
                    final var newInst = current.getByLoad(load);

                    if (newInst != load) {
                        load.replaceAllUseWith(newInst);
                        load.freeFromIList();
                        load.freeFromUseDef();
                    } else {
                        current.setByLoad(load);
                    }
                }
            }
        }
    }
}

