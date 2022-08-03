package ir;

import java.util.*;
import java.util.stream.Collectors;

import ir.constant.ArrayConst;

public class Module {
    public Set<Function> getFunctions() {
        return functions;
    }

    public Set<Function> getNonExternalFunction() {
        return functions.stream().filter(f -> !f.isExternal()).collect(Collectors.toUnmodifiableSet());
    }

    public Set<ArrayConst> getArrayConstants() {
        return arrayConst;
    }

    public Set<GlobalVar> getVariables() {
        return variables;
    }

    public void verifyAll() {
        for (final var func : functions) {
            func.verifyAll();
        }

        for (final var global : variables) {
            global.verifyAll();
        }

        for (final var constant : arrayConst) {
            constant.verifyAll();
        }
    }

    private final Set<Function> functions = new LinkedHashSet<>();
    private final Set<GlobalVar> variables = new LinkedHashSet<>();
    private final Set<ArrayConst> arrayConst = new LinkedHashSet<>();
}