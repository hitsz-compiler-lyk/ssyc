package pass.ir;

import ir.BasicBlock;
import ir.Module;
import ir.inst.Instruction;

import java.util.*;
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

    static <K, V> Map<K, V> copyForChange(Map<K, V> map) { return new HashMap<>(map); }

    static Stream<Instruction> instructionStream(Module module) {
        return module.getNonExternalFunction().stream()
            .flatMap(List<BasicBlock>::stream)
            .flatMap(List<Instruction>::stream);
    }
}
