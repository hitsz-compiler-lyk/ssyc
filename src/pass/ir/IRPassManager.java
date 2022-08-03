package pass.ir;

import ir.GlobalModifitationStatus;
import ir.Module;
import utils.Log;

public class IRPassManager {
    public IRPassManager(Module module) {
        this.module = module;
        this.passCount = 0;
    }

    public void runAllPasses() {
        GlobalModifitationStatus.doUntilNoChange(() -> {
            runDefaultBlockClearUpPasses();
            runPass(new FunctionInline());
            runPass(new ClearUselessFunction());
            runDefaultBlockClearUpPasses();
            runPass(new ConstructDominatorInfo());
            runPass(new SimpleGVN());
            runDefaultBlockClearUpPasses();
            runPass(new ReplaceUnessaryLoad());
            runDefaultBlockClearUpPasses();
        });
    }

    public void runDefaultBlockClearUpPasses() {
        GlobalModifitationStatus.doUntilNoChange(() -> {
            runDefaultInstructionClearUpPasses();
            runPass(new ClearUnreachableBlock());
            runDefaultInstructionClearUpPasses();
            runPass(new MergeDirectBranch());
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

    public class IRPassException extends RuntimeException {
        IRPassException(int index, String passName, Exception cause) {
            super("IRPass exception on #%d:%s".formatted(index, passName), cause);
        }
    }
}
