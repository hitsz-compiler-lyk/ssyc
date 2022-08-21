package pass.ir;

import ir.Module;
import ir.inst.CallInst;
import ir.inst.Instruction;
import ir.inst.PhiInst;

public class ClearUselessInstruction implements IRPass {
    @Override
    public void runPass(Module module) {
        IRPass.instructionStream(module)
            .filter(ClearUselessInstruction::canBeRemove)
            .forEach(ClearUselessInstruction::deleteInstruction);
    }

    public static boolean canBeRemove(Instruction inst) {
        final var hasSideEffect = inst.getType().isVoid() || inst instanceof CallInst;
        return !hasSideEffect && inst.haveNoUser();
    }

    public static void deleteInstruction(Instruction inst) {
        // TODO: 思考是否将调整 block 的 phiEnd 的操作放到 phi 的 freeFromIList 里面
        if (inst instanceof PhiInst) {
            final var block = inst.getParent();
            inst.freeAll();
            block.adjustPhiEnd();
        } else {
            inst.freeAll();
        }
    }
}
