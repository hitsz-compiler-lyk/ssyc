package top.origami404.ssyc.pass.ir;

import top.origami404.ssyc.frontend.IRBuilder;
import top.origami404.ssyc.ir.Module;

public class ConstantFold implements IRPass {
    @Override
    public void runPass(final Module module) {
        for (final var func : module.getNonExternalFunction()) {
            for (final var block : func.getBasicBlocks()) {
                block.allInst().forEach(IRBuilder::refold);
            }
        }
    }
}
