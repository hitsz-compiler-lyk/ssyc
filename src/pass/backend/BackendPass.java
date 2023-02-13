package pass.backend;

import backend.lir.ArmModule;

public interface BackendPass {
    default String getPassName() {
        return getClass().getSimpleName();
    }

    void runPass(ArmModule module);
}
