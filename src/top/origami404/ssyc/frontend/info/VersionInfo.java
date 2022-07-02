package top.origami404.ssyc.frontend.info;

import java.util.Map;
import java.util.Optional;

import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.analysis.AnalysisInfo;
import top.origami404.ssyc.ir.inst.Instruction;

/**
 * <p>  保存翻译时块中 "源语言变量" --> "当前对应的版本" 的信息
 *
 * <p>  IR 生成时与作用域的表相配合. 作用域负责将 "源语言中的标识符" 对应到 "源语言中的变量",
 *      而该类则负责维护变量的定义
 *
 * @see IRGen
 */
public class VersionInfo implements AnalysisInfo {
    /**
     * 代表了源语言里的一个特定的变量
     */
    public record Variable(String name, int lineNo) {
        public String getIRName() {
            return "%" + name + "$" + lineNo;
        }
    }

    public boolean contains(Variable var) {
        return version.containsKey(var);
    }

    public Optional<Value> getDef(Variable var) {
        return getInfo(var).map(VarVersionInfo::getCurrDef);
    }

    public void kill(Variable var, Value newDef) {
        final var info = getInfo(var)
            .orElseThrow(() -> new RuntimeException("Variable " + var + "was undefined."));

        info.kill(newDef);
    }

    public void newDef(Variable var, Value initVal) {
        if (version.containsKey(var)) {
            throw new RuntimeException("Identifier has already defined");
        }

        version.put(var, new VarVersionInfo(var, initVal));
    }

    public Optional<VarVersionInfo> getInfo(Variable var) {
        return Optional.ofNullable(version.get(var));
    }

    public class VarVersionInfo {
        VarVersionInfo(Variable variable, Value initVal) {
            this.variable = variable;
            this.currDef = initVal;
            this.version = 0;
        }

        public Value getCurrDef() {
            return currDef;
        }

        public void kill(Value newDef) {
            this.currDef = newDef;
            this.version += 1;

            if (newDef instanceof Instruction inst) {
                inst.setName(variable.getIRName() + "_" + version);
            }
        }

        public int getVersion() {
            return version;
        }

        public String getName() {
            return variable.name;
        }

        public int getLineNo() {
            return variable.lineNo;
        }

        private final Variable variable;
        private Value currDef;
        private int version;
    }

    private Map<Variable, VarVersionInfo> version;
}
