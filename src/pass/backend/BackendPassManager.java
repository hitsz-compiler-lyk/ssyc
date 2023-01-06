package pass.backend;

import backend.codegen.CodeGenManager;
import backend.lir.ArmModule;

public class BackendPassManager {
    private final ArmModule module;
    public BackendPassManager(ArmModule module) {
        this.module = module;
    }

    public void runAllPasses() {
        runPass(new Peephole());
        runPass(new BranchToCond());
    }

    private void runPass(BackendPass pass) {
        pass.runPass(module);
    }
}
