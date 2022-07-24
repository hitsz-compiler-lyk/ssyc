package top.origami404.ssyc.pass.ir;

import top.origami404.ssyc.ir.Module;
import top.origami404.ssyc.ir.Value;

import java.util.HashSet;

public class ClearUselessFunction implements IRPass {
    @Override
    public void runPass(final Module module) {
        final var oldFunctions = new HashSet<>(module.getFunctions());
        oldFunctions.stream()
           .filter(func -> !func.getFunctionSourceName().equals("main"))
           .filter(Value::isUseless)
           .forEach(module.getFunctions()::remove);
    }
}
