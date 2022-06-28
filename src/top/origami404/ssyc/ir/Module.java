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

    public Map<String, AllocInst> getVariables() {
        return variables;
    }

    private Map<String, Function> functions;
    private Map<String, AllocInst> variables;
    private Map<String, Constant> constants;
}
