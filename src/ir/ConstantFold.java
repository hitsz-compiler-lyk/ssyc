package ir;

import frontend.IRBuilder;

public class ConstantFold implements IRPass {
    @Override
    public void runPass(final Module module) {
        IRPass.instructionStream(module).forEach(IRBuilder::refold);
    }
}
