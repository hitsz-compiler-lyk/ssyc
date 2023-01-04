package pass.ir.memory;

import ir.BasicBlock;
import ir.Function;
import ir.Module;
import ir.constant.Constant;
import ir.inst.*;
import pass.ir.IRPass;
import utils.INodeOwner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

public class LocalArrayHoist implements IRPass {
    @Override
    public void runPass(final Module module) {
        module.getNonExternalFunction().forEach(this::runOnFunction);
    }

    public void runOnFunction(Function function) {
        final var entry = function.getEntryBBlock();
        final var nonEntries = nonEntries(function);
        for (final var block : nonEntries) {
            for (final var inst : block) {
                if (inst instanceof CAllocInst) {
                    final var stores = findLocalArrayUsage(inst)
                        .filter(StoreInst.class::isInstance).map(StoreInst.class::cast).toList();

                    final var hasOutsideStore = stores.stream()
                        .map(INodeOwner::getParent)
                        .anyMatch(blockOfStoreUsage -> blockOfStoreUsage != block);

                    final var allStoreValuesAreConstant = stores.stream()
                        .map(StoreInst::getVal)
                        .allMatch(Constant.class::isInstance);

                    final var allStorePtrAreGEP = stores.stream()
                        .map(StoreInst::getPtr).allMatch(GEPInst.class::isInstance);

                    final var allStoreGEPAreConstant = stores.stream()
                        .map(StoreInst::getPtr)
                        .filter(GEPInst.class::isInstance).map(GEPInst.class::cast)
                        .map(GEPInst::getIndices).flatMap(Collection::stream)
                        .allMatch(Constant.class::isInstance);

                    if (!hasOutsideStore && allStoreValuesAreConstant && allStorePtrAreGEP && allStoreGEPAreConstant) {
                        // move calloc and all store with its gep to entry
                        entry.addInstBeforeTerminator(inst);
                        // 还有一个 MemInit 也要移
                        entry.addInstBeforeTerminator(findMemInit(inst));
                        // 要把 store 跟 GEP 放一起, 减少 GEP 的生存周期
                        stores.stream()
                            .flatMap(store -> Stream.of((GEPInst) store.getPtr(), store))
                            .forEach(entry::addInstBeforeTerminator);
                    }
                }
            }
        }
    }

    Stream<Instruction> findLocalArrayUsage(Instruction instruction) {
        if (instruction instanceof GEPInst || instruction instanceof CAllocInst) {
            return instruction.getUserList().stream()
                .filter(Instruction.class::isInstance).map(Instruction.class::cast)
                .flatMap(this::findLocalArrayUsage);
        } else {
            return Stream.of(instruction);
        }
    }

    MemInitInst findMemInit(Instruction calloc) {
        return calloc.getUserList().stream()
            .filter(MemInitInst.class::isInstance).map(MemInitInst.class::cast)
            .findAny().orElseThrow();
    }

    List<BasicBlock> nonEntries(List<BasicBlock> blocks) {
        final var result = new ArrayList<>(blocks);
        result.remove(0);
        return result;
    }
}
