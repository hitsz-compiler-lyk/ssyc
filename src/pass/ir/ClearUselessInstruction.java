package pass.ir;

import ir.Module;
import ir.inst.CallInst;
import ir.inst.Instruction;

public class ClearUselessInstruction implements IRPass {
    @Override
    public void runPass(Module module) {
        IRPass.instructionStream(module).filter(this::canBeRemove).forEach(Instruction::freeAll);
    }

    public boolean canBeRemove(Instruction inst) {
        final var hasSideEffect = inst.getType().isVoid() || inst instanceof CallInst;
        return !hasSideEffect && inst.isUseless();
    }
}
