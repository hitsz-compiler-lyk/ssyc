package top.origami404.ssyc.pass.ir;

import top.origami404.ssyc.ir.Module;
import top.origami404.ssyc.ir.Value;

public class ClearUselessFunction implements IRPass {
    @Override
    public void runPass(final Module module) {
        IRPass.copyForChange(module.getFunctions()).stream()
           .filter(func -> !func.getFunctionSourceName().equals("main"))
           .filter(Value::isUseless)
           .forEach(module.getFunctions()::remove);
    }
}
