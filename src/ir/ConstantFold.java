package ir;

import frontend.IRBuilder;

public class ConstantFold implements IRPass {
    @Override
    public void runPass(final Module module) {
        for (final var func : module.getNonExternalFunction()) {
            for (final var block : func) {
                block.allInst().forEach(IRBuilder::refold);
            }
        }
    }
}
