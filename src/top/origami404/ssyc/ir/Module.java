package top.origami404.ssyc.ir;

import java.util.Map;
import java.util.Optional;

import top.origami404.ssyc.ir.Function;
import top.origami404.ssyc.ir.constant.Constant;
import top.origami404.ssyc.ir.inst.AllocInst;

public class Module {
    public Optional<Function> getFunction(String name) {
        return Optional.ofNullable(functions.get(name));
    }

    public Optional<AllocInst> getGlobalVar(String name) {
        return Optional.ofNullable(variables.get(name));
    }

    public Optional<Constant> getGlobalConst(String name) {
        return Optional.ofNullable(constants.get(name));
    }

    public void putFunction(Function func) {
        functions.put(func.getName(), func);
    }

    public void putGlobalVar(AllocInst var) {
        variables.put(var.getName(), var);
    }

    public void putGlobalConst(String name, Constant constant) {
        constants.put(name, constant);
    }

    private Map<String, Function> functions;
    private Map<String, AllocInst> variables;
    private Map<String, Constant> constants;
}
