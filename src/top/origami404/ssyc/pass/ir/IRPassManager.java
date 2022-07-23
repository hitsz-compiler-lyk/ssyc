package top.origami404.ssyc.pass.ir;

import top.origami404.ssyc.ir.Module;

import java.util.ArrayList;
import java.util.List;

public class IRPassManager {
    public void runAllPass(Module module) {
        final var size = irPasses.size();
        for (int i = 0; i < size; i++) {
            runOne(i, module);
        }
    }

    public void addDefaultPasses() {
        addDefaultClearUpPasses();
        addPass(new FunctionInline());
        addDefaultClearUpPasses();
    }

    public void addDefaultClearUpPasses() {
        addPass(new ClearUnreachableBlock());
        addPass(new ConstantFold());
        addPass(new RemoveTravialPhi());
        addPass(new MergeDirectBranch());
        addPass(new ConstantFold());
        addPass(new RemoveTravialPhi());
        addPass(new ConstantFold());
    }

    private void runOne(int index, Module module) {
        try {
            final var pass = irPasses.get(index);
            pass.runPass(module);
        } catch (Exception e) {
            throw new IRPassException(index, e);
        }
    }

    private void addPass(IRPass pass) {
        irPasses.add(pass);
    }

    private final List<IRPass> irPasses = new ArrayList<>();

    public class IRPassException extends RuntimeException {
        IRPassException(int index, Exception cause) {
            super("IRPass exception on #%d:%s".formatted(index, irPasses.get(index).getClass().getSimpleName()), cause);
        }
    }
}
