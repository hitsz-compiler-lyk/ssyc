package pass.ir;

import ir.GlobalVar;
import ir.Module;
import ir.inst.Instruction;
import ir.inst.LoadInst;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HoistGlobalArrayLoad implements IRPass {
    private final Map<GlobalVar, Instruction> loadOfGlobalArrays = new HashMap<>();

    @Override
    public void runPass(final Module module) {
        for (final var function : module.getNonExternalFunction()) {
            loadOfGlobalArrays.clear();
            for (final var gv : module.getVariables()) {
                if (gv.isArray()) {
                    final var load = new LoadInst(gv);
                    load.setSymbol(gv.getSymbol());
                    loadOfGlobalArrays.put(gv, load);
                }
            }

            function.stream().flatMap(List::stream).forEach(this::tryReplaceLoadForGlobalArray);

            final var entry = function.getEntryBBlock();
            loadOfGlobalArrays.values().forEach(entry::addInstAfterPhi);
            entry.adjustPhiEnd();
        }
    }

    void tryReplaceLoadForGlobalArray(Instruction instruction) {
        if (instruction instanceof final LoadInst load) {
            final var ptr = load.getPtr();

            if (ptr instanceof final GlobalVar gv) {

                if (gv.isArray()) {
                    final var newLoad = loadOfGlobalArrays.get(gv);

                    instruction.replaceAllUseWith(newLoad);
                    instruction.freeAll();
                }
            }
        }
    }
}
