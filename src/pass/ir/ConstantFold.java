package pass.ir;

import frontend.IRBuilder;
import ir.Module;

public class ConstantFold implements IRPass {
    @Override
    public void runPass(final Module module) {
        IRPass.instructionStream(module).forEach(IRBuilder::refold);
    }
}
