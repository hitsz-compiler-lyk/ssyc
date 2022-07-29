package top.origami404.ssyc.pass.ir;

import top.origami404.ssyc.ir.BasicBlock;
import top.origami404.ssyc.ir.Module;
import top.origami404.ssyc.ir.inst.*;
import top.origami404.ssyc.pass.ir.ConstructDominatorInfo.DominatorInfo;
import top.origami404.ssyc.utils.ChainMap;

import java.util.*;

/** 依赖 ConstructDominatorInfo */
public class SimpleGVN implements IRPass {
    @Override
    public void runPass(final Module module) {
        for (final var func : module.getNonExternalFunction()) {
            uniqueNormalExp(func.getEntryBBlock(), new ChainMap<>());
        }
    }

    void uniqueNormalExp(BasicBlock block, ChainMap<Integer, Instruction> idomAvailable) {
        final var available = new ChainMap<>(idomAvailable);

        for (final var inst : block) {
            if (canNotBeUniqued(inst)) {
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

    boolean canNotBeUniqued(Instruction inst) {
        if (inst.getType().isVoid()) {
            return true;
        }

        final var classesUnableToUnique = Set.of(
            CAllocInst.class, CallInst.class, LoadInst.class, PhiInst.class
        );

        return classesUnableToUnique.contains(inst.getClass());
    }
}
