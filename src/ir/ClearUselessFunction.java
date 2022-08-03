package ir;

public class ClearUselessFunction implements IRPass {
    @Override
    public void runPass(final Module module) {
        IRPass.copyForChange(module.getFunctions()).stream()
           .filter(func -> !func.getFunctionSourceName().equals("main"))
           .filter(Value::isUseless)
           .forEach(module.getFunctions()::remove);
    }
}
