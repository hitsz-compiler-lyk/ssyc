package top.origami404.ssyc.pass.ir;

import top.origami404.ssyc.ir.Module;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public interface IRPass {
    default String getPassName() {
        return getClass().getSimpleName();
    }

    void runPass(Module module);

    default void runUntilFalse(BooleanSupplier supplier) {
        while (supplier.getAsBoolean()) {
            assert true; // do nothing
        }
    }
}
