package top.origami404.ssyc.pass.ir;

import top.origami404.ssyc.ir.Module;

public interface IRPass {
    default String getPassName() {
        return getClass().getSimpleName();
    }

    void run(Module module);
}
