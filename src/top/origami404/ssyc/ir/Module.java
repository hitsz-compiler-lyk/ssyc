package top.origami404.ssyc.ir;

import java.util.Map;

import top.origami404.ssyc.ir.constant.ArrayConst;

public class Module {
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

    private Map<String, Function> functions;
    private Map<String, GlobalVar> variables;
    private Map<String, ArrayConst> arrayConst;
}
