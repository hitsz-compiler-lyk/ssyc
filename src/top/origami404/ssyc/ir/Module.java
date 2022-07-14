package top.origami404.ssyc.ir;

import java.util.HashMap;
import java.util.Map;

import top.origami404.ssyc.ir.constant.ArrayConst;

public class Module {
    public Module() {
        this.functions = new HashMap<>();
        this.variables = new HashMap<>();
        this.arrayConst = new HashMap<>();
    }

    public Map<String, Function> getFunctions() {
        return functions;
    }

    public Map<String, ArrayConst> getArrayConstants() {
        return arrayConst;
    }

    public Map<String, GlobalVar> getVariables() {
        return variables;
    }

    public void verifyAll() {
        for (final var func : functions.values()) {
            func.verifyAll();
        }

        for (final var global : variables.values()) {
            global.verifyAll();
        }

        for (final var constant : arrayConst.values()) {
            constant.verifyAll();
        }
    }

    private final Map<String, Function> functions;
    private final Map<String, GlobalVar> variables;
    private final Map<String, ArrayConst> arrayConst;
}
