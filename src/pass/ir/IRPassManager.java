package pass.ir;

import ir.GlobalModifitationStatus;
import ir.Module;
import pass.ir.loop.LoopUnroll;
import pass.ir.memory.RemoveUnnecessaryArray;
import pass.ir.memory.ReplaceUnnecessaryLoad;
import utils.Log;

public class IRPassManager {
    public IRPassManager(Module module) {
        this.module = module;
        this.passCount = 0;
    }

    public void runAllPasses() {
        runAllClearUpPasses();
        runPass(new LoopUnroll());
        runAllClearUpPasses();
    }

    public void runAllClearUpPasses() {
        GlobalModifitationStatus.doUntilNoChange(() -> {
            runDefaultBlockClearUpPasses();
            runPass(new FunctionInline());
            runPass(new ClearUselessFunction());
            runDefaultBlockClearUpPasses();
            runPass(new SimpleGVN());
            runDefaultBlockClearUpPasses();
            runMemoryOptimizePass();
            runDefaultBlockClearUpPasses();
        });
    }

    public void runMemoryOptimizePass() {
        GlobalModifitationStatus.doUntilNoChange(() -> {
            runPass(new ReplaceUnnecessaryLoad());
            runDefaultBlockClearUpPasses();
            runPass(new RemoveUnnecessaryArray());
            runDefaultBlockClearUpPasses();
        });
    }

    public void runDefaultBlockClearUpPasses() {
        GlobalModifitationStatus.doUntilNoChange(() -> {
            runDefaultInstructionClearUpPasses();
            runPass(new ClearUnreachableBlock());
            runDefaultInstructionClearUpPasses();
            runPass(new FuseBasicBlock());
            runDefaultInstructionClearUpPasses();
            runPass(new FuseImmediatelyBranch());
            runDefaultInstructionClearUpPasses();
            runPass(new ClearUnreachableBlock());
            runDefaultInstructionClearUpPasses();
        });
    }

    public void runDefaultInstructionClearUpPasses() {
        GlobalModifitationStatus.doUntilNoChange(() -> {
            runPass(new ConstantFold());
            runPass(new RemoveTravialPhi());
            runPass(new ClearUselessInstruction());
        });
    }

    private final Module module;
    private int passCount;

    public void runPass(IRPass pass) {
        final var index = passCount++;
        final var name = pass.getPassName();
        try {
            Log.info("Begin run #%d:%s".formatted(index, name));
            pass.runPass(module);
        } catch (Exception e) {
            throw new IRPassException(index, name, e);
        }
    }

    public static class IRPassException extends RuntimeException {
        IRPassException(int index, String passName, Exception cause) {
            super("IRPass exception on #%d:%s".formatted(index, passName), cause);
        }
    }
}
