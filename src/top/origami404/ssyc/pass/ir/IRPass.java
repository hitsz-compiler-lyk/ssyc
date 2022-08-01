package top.origami404.ssyc.pass.ir;

import top.origami404.ssyc.ir.BasicBlock;
import top.origami404.ssyc.ir.Module;
import top.origami404.ssyc.ir.inst.Instruction;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
public interface IRPass {
    default String getPassName() {
        return getClass().getSimpleName();
    }

    void runPass(Module module);

    static <E> List<E> copyForChange(List<E> list) {
        return new ArrayList<>(list);
    }

    static <E> Set<E> copyForChange(Set<E> set) {
        return new HashSet<>(set);
    }

    static Stream<Instruction> instructionStream(Module module) {
        return module.getNonExternalFunction().stream()
            .flatMap(List<BasicBlock>::stream)
            .flatMap(List<Instruction>::stream);
    }
}
