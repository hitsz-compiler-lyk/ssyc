package top.origami404.ssyc.ir;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import top.origami404.ssyc.ir.arg.Function;
import top.origami404.ssyc.ir.arg.PtrReg;

public class Module {
    public Module(String name) {
        this.name = name;
        this.functions = new HashMap<>();
        this.globalVars = new HashMap<>();
    }

    public String toTextForm() {
        return "";
    }

    public String getName() {
        return name;
    }

    public Optional<Function> getFunction(String name) {
        return Optional.ofNullable(functions.get(name));
    }

    public void addFunction(Function func) {
        functions.put(func.getName(), func);
    }

    public Optional<PtrReg> getGlobalVar(String name) {
        return Optional.ofNullable(globalVars.get(name));
    }

    public void addGlobalVar(PtrReg var) {
        globalVars.put(var.getName(), var);
    }

    private final String name;
    private Map<String, Function> functions;
    private Map<String, PtrReg> globalVars;
}
