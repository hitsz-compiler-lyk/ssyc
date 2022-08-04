package pass.ir.memory;

import ir.*;
import ir.Module;
import ir.inst.*;
import pass.ir.IRPass;
import utils.INodeOwner;

import java.util.List;

public class RemoveUnnecessaryArray implements IRPass {
    @Override
    public void runPass(final Module module) {
        module.getVariables().forEach(this::dealWithGlobalArray);
        module.getNonExternalFunction().stream()
            .flatMap(List<BasicBlock>::stream)
            .forEach(this::dealWithLocalArray);
    }

    void dealWithGlobalArray(GlobalVar gv) {
        final var hasNoLoad = gv.getUserList().stream()
            .noneMatch(gv.isArray() ? this::isGlobalArrayLoad : this::isGlobalVariableLoad);

        if (hasNoLoad) {
            removeUserTree(gv);
        }
    }

    boolean isGlobalArrayLoad(Value value) {
        if (value instanceof LoadInst) {
            final var loadOfArray = (LoadInst) value;
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
            if (inst instanceof CAllocInst) {
                final var calloc = (CAllocInst) inst;
                final var hasNoLoad = calloc.getUserList().stream().noneMatch(this::isLocalLoad);

                if (hasNoLoad) {
                    removeUserTree(calloc);
                }
            }
        }
    }

    boolean isLocalLoad(Value value) {
        if (value instanceof GEPInst) {
            final var gep = (GEPInst) value;
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
        if (value instanceof User) {
            final var user = (User) value;
            IRPass.copyForChange(user.getUserList()).forEach(this::removeUserTree);
            user.freeFromUseDef();
        }

        if (value instanceof INodeOwner) {
            final var owner = (INodeOwner) value;
            owner.freeFromIList();
        }
    }
}
