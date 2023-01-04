package pass.ir.memory;

import ir.Module;
import ir.*;
import ir.inst.CAllocInst;
import ir.inst.CallInst;
import ir.inst.GEPInst;
import ir.inst.LoadInst;
import pass.ir.IRPass;
import utils.INodeOwner;

import java.util.List;

/**
 * 将只有写入没有读取的内存变量整个消除
 */
public class RemoveUnnecessaryArray implements IRPass {
    @Override
    public void runPass(final Module module) {
        currModule = module;
        IRPass.copyForChange(module.getVariables())
            .forEach(this::dealWithGlobalArray);
        module.getNonExternalFunction().stream()
            .flatMap(List<BasicBlock>::stream)
            .forEach(this::dealWithLocalArray);
    }

    Module currModule;
    void dealWithGlobalArray(GlobalVar gv) {
        final var hasNoLoad = gv.getUserList().stream()
            .noneMatch(gv.isArray() ? this::isGlobalArrayLoad : this::isGlobalVariableLoad);

        if (hasNoLoad) {
            removeUserTree(gv);
            currModule.getVariables().remove(gv);
        }
    }

    boolean isGlobalArrayLoad(Value value) {
        if (value instanceof final LoadInst loadOfArray) {
            return loadOfArray.getUserList().stream().anyMatch(this::isLocalLoad);
        } else {
            return false;
        }
    }

    boolean isGlobalVariableLoad(Value value) {
        return isUse(value);
    }


    void dealWithLocalArray(BasicBlock block) {
        for (final var inst : block) {
            if (inst instanceof final CAllocInst calloc) {
                final var hasNoLoad = calloc.getUserList().stream().noneMatch(this::isLocalLoad);

                if (hasNoLoad) {
                    removeUserTree(calloc);
                }
            }
        }
    }

    boolean isLocalLoad(Value value) {
        if (value instanceof final GEPInst gep) {
            return gep.getUserList().stream().anyMatch(this::isUse);
        } else {
            return value instanceof CallInst;
        }
    }

    boolean isUse(Value value) {
        return !value.getType().isVoid() || value instanceof CallInst;
    }

    /** 递归删除所有 user 以及 user 的 user 以及 ... */
    void removeUserTree(Value value) {
        IRPass.copyForChange(value.getUserList()).forEach(this::removeUserTree);
        if (value instanceof User) {
            ((User) value).freeFromUseDef();
        }

        if (value instanceof INodeOwner owner) {
            owner.freeFromIList();
        }
    }
}
