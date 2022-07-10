package top.origami404.ssyc.pass.ir;

import top.origami404.ssyc.ir.Module;
import top.origami404.ssyc.ir.inst.BrInst;

public class RemoveUselessBlock implements IRPass {
    @Override
    public void run(Module module) {
        for (final var func : module.getFunctions().values()) {
            for (final var block : func.getBasicBlocks()) {
                final var instList = block.getIList().asElementView();
                if (instList.size() == 1 && instList.get(0) instanceof BrInst) {
                    final var brInst = (BrInst) instList.get(0);
                    block.replaceAllUseWith(brInst.getNextBB());
                    func.getIList().asElementView().remove(block);
                }
                // TODO: 处理有 phi 的情况
            }
        }
    }
}
