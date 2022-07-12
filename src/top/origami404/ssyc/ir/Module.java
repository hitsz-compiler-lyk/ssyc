package top.origami404.ssyc.ir;

import java.util.Map;

import top.origami404.ssyc.ir.Function;
import top.origami404.ssyc.ir.constant.Constant;
import top.origami404.ssyc.ir.inst.AllocInst;

public class Module {
    public Map<String, Function> getFunctions() {
        return functions;
    }

    public Map<String, Constant> getConstants() {
        return constants;
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

        for (final var constant : constants.values()) {
            constant.verifyAll();
        }
    }

    private Map<String, Function> functions;
    private Map<String, GlobalVar> variables;
    private Map<String, Constant> constants;
}
