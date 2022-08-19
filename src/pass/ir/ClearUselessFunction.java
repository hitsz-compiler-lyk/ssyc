package pass.ir;

import ir.Module;
import ir.Value;

public class ClearUselessFunction implements IRPass {
    @Override
    public void runPass(final Module module) {
        IRPass.copyForChange(module.getFunctions()).stream()
           .filter(func -> !func.getFunctionSourceName().equals("main"))
           .filter(Value::haveNoUser)
           .forEach(module.getFunctions()::remove);
    }
}
