package pass.ir;

import ir.GlobalModificationStatus;
import ir.Module;
import pass.ir.loop.FullyUnroll;
import pass.ir.loop.InductionVariableReduce;
import pass.ir.loop.LoopUnroll;
import pass.ir.loop.SimpleInvariantHoist;
import pass.ir.memory.LocalArrayHoist;
import pass.ir.memory.RemoveUnnecessaryArray;
import pass.ir.memory.ReplaceConstantArray;
import pass.ir.memory.ReplaceUnnecessaryLoad;
import utils.Log;

public class IRPassManager {
    public IRPassManager(Module module) {
        this.module = module;
        this.passCount = 0;
    }

    public void runAllPasses() {
        runPass(new RemoveCurrDef());
        runAllClearUpPasses();
        runGlobalVariableToValuePass();
        runPass(new ReplaceConstantArray());
        runMemoryOptimizePass();
        runPass(new FullyUnroll());
        runPass(new ReplaceConstantArray());
        runDefaultBlockClearUpPasses();
        runMemoryOptimizePass();
        runPass(new LocalArrayHoist());
        runPass(new HoistGlobalArrayLoad());
        runPass(new SimpleInvariantHoist());
        runAllClearUpPasses();
        runPass(new InductionVariableReduce());
        runPass(new LoopUnroll());
        runAllClearUpPasses();

        runPass(new LCM());
    }

    public void runAllClearUpPasses() {
        GlobalModificationStatus.doUntilNoChange(() -> {
            runDefaultBlockClearUpPasses();
            runPass(new FunctionInline());
            runDefaultBlockClearUpPasses();
            runPass(new ClearUselessFunction());
            runDefaultBlockClearUpPasses();
            runPass(new SimpleGVN());
            runDefaultBlockClearUpPasses();
        });
    }

    public void runGlobalVariableToValuePass() {
        runPass(new GlobalVariableToValue());

        GlobalModificationStatus.doUntilNoChange(() -> {
            runDefaultBlockClearUpPasses();
            runPass(new SimpleGVN());
            runDefaultBlockClearUpPasses();
        });
    }

    public void runMemoryOptimizePass() {
        GlobalModificationStatus.doUntilNoChange(() -> {
            runPass(new ReplaceUnnecessaryLoad());
            runDefaultBlockClearUpPasses();
            runPass(new RemoveUnnecessaryArray());
            runDefaultBlockClearUpPasses();
        });
    }

    public void runDefaultBlockClearUpPasses() {
        GlobalModificationStatus.doUntilNoChange(() -> {
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

        runPass(new BlockReorder());
    }

    public void runDefaultInstructionClearUpPasses() {
        GlobalModificationStatus.doUntilNoChange(() -> {
            runPass(new ClearUnreachableBlock());
            runPass(new InstructionCombiner());
            runPass(new ConstantFold());
            runPass(new RemoveTrivialPhi());
            runPass(new ClearUselessInstruction());
            runPass(new ClearUnreachableBlock());
            runPass(new GCM());
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
