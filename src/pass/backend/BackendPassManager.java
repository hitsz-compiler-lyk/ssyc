package pass.backend;

import backend.codegen.CodeGenManager;

public class BackendPassManager {
    private CodeGenManager manager;
    public BackendPassManager(CodeGenManager manager) {
        this.manager = manager;
    }

    public void runAllPasses() {
        runPass(new Peephole());
        runPass(new BranchToCond());
    }

    private void runPass(BackendPass pass) {
        pass.runPass(manager);
    }
}
