package pass.ir.memory;

import ir.GlobalVar;
import ir.Module;
import ir.Value;
import ir.inst.GEPInst;
import ir.inst.Instruction;
import ir.inst.LoadInst;
import ir.inst.StoreInst;
import pass.ir.IRPass;

import java.util.List;

public class ReplaceConstantArray implements IRPass {
    private Module module;

    @Override
    public void runPass(final Module module) {
        this.module = module;

        final var constantGlobalArrays = module.getVariables().stream()
            .filter(GlobalVar::isArray)
            .filter(this::isGlobalConstantArray).toList();

        for (final var array : constantGlobalArrays) {
            final var gvMemVar = MemVariable.createWithGlobalVariable(array);
            final var cache = MemCache.empty();
            cache.setByGlobalVar(array);

            final Iterable<Instruction> instructions = () -> IRPass.instructionStream(module).iterator();
            for (final var inst : instructions) {
                if (inst instanceof final LoadInst load) {
                    MemVariable.createWithLoad(load).ifPresent(memVar -> {
                        if (memVar.equals(gvMemVar)) {
                            final var valueInPosition = cache.getByLoad(load);
                            // 如果这是一个变量的 Load, 那 cache 也会返回 load 自己
                            // Log.ensure(valueInPosition != load,
                            //     "A constant global array must have a value for any position");
                            if (valueInPosition != load) {
                                load.replaceAllUseWith(valueInPosition);
                                load.freeAll();
                            }
                        }
                    });
                }
            }
        }
    }

    boolean isGlobalConstantArray(GlobalVar gv) {
        final var gvMemVar = MemVariable.createWithGlobalVariable(gv);

        final var hasStore = IRPass.instructionStream(module)
            .filter(StoreInst.class::isInstance).map(StoreInst.class::cast)
            .map(MemVariable::createWithStore)
            .anyMatch(gvMemVar::equals);

        final var notHavingRawUse = gv.getUserList().stream()
            .map(Value::getUserList)
            .flatMap(List::stream)
            .allMatch(GEPInst.class::isInstance);

        return !hasStore && notHavingRawUse;
    }
}
