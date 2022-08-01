package top.origami404.ssyc.pass.ir;

import top.origami404.ssyc.frontend.IRBuilder;
import top.origami404.ssyc.ir.Module;

public class ConstantFold implements IRPass {
    @Override
    public void runPass(final Module module) {
        IRPass.instructionStream(module).forEach(IRBuilder::refold);
    }
}
