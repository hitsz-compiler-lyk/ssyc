package top.origami404.ssyc.pass.ir;

import top.origami404.ssyc.ir.Module;

import java.util.*;
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

    static <E> List<E> copyForChange(List<E> list) {
        return new ArrayList<>(list);
    }

    static <E> Set<E> copyForChange(Set<E> set) {
        return new HashSet<>(set);
    }
}
