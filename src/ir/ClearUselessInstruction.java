package top.origami404.ssyc.pass.ir;

import top.origami404.ssyc.ir.Module;
import top.origami404.ssyc.ir.inst.CallInst;
import top.origami404.ssyc.ir.inst.Instruction;

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
