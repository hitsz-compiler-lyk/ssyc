package pass.backend;

import backend.codegen.CodeGenManager;

public interface BackendPass {
    default String getPassName() {
        return getClass().getSimpleName();
    }

    void runPass(CodeGenManager manager);
}
