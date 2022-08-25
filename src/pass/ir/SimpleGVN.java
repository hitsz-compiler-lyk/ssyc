package pass.ir;

import ir.BasicBlock;
import ir.Module;
import ir.inst.*;
import pass.ir.ConstructDominatorInfo.DominatorInfo;
import utils.ChainMap;

import java.util.Set;

/** 依赖 ConstructDominatorInfo */
public class SimpleGVN implements IRPass {
    @Override
    public void runPass(final Module module) {
        final var collector = new ConstructDominatorInfo();
        collector.runPass(module);

        for (final var func : module.getNonExternalFunction()) {
            uniqueNormalExp(func.getEntryBBlock(), new ChainMap<>());
        }
    }

    void uniqueNormalExp(BasicBlock block, ChainMap<Integer, Instruction> idomAvailable) {
        final var available = new ChainMap<>(idomAvailable);

        for (final var inst : block) {
            if (canNotBeUnique(inst)) {
                continue;
            }

            final var hash = inst.hashCode();
            available.get(hash).ifPresentOrElse(cache -> {
                inst.replaceAllUseWith(cache);
                inst.freeFromIList();
                inst.freeFromUseDef();
            }, () -> available.put(hash, inst));
        }

        for (final var child : DominatorInfo.domTreeChildren(block)) {
            uniqueNormalExp(child, available);
        }
    }

    boolean canNotBeUnique(Instruction inst) {
        if (inst.getType().isVoid()) {
            return true;
        }

        final var classesUnableToUnique = Set.of(
            CAllocInst.class, CallInst.class, LoadInst.class, PhiInst.class
        );

        return classesUnableToUnique.contains(inst.getClass());
    }
}
