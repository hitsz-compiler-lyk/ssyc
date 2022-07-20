package top.origami404.ssyc.frontend.info;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import top.origami404.ssyc.frontend.SourceCodeSymbol;
import top.origami404.ssyc.ir.User;
import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.analysis.AnalysisInfo;
import top.origami404.ssyc.ir.type.IRType;

/**
 * <p>  保存翻译时块中 "源语言变量" --> "当前对应的版本" 的信息
 *
 * <p>  IR 生成时与作用域的表相配合. 作用域负责将 "源语言中的标识符" 对应到 "源语言中的变量",
 *      而该类则负责维护变量的定义
 *
 * @see ../IRGen
 */
public class CurrDefInfo implements AnalysisInfo {
    public boolean hasDef(SourceCodeSymbol symbol) {
        return definitions.containsKey(symbol);
    }

    public Optional<Value> getDefOpt(SourceCodeSymbol symbol) {
        return getEntryOpt(symbol).map(Entry::getCurrDef);
    }

    public Value getDef(SourceCodeSymbol symbol) {
        return getEntryOpt(symbol)
            .orElseThrow(() -> new RuntimeException("symbol not found: " + symbol))
            .getCurrDef();
    }


    public void newDef(SourceCodeSymbol symbol, Value initVal) {
        if (definitions.containsKey(symbol)) {
            throw new RuntimeException("Identifier has already defined");
        }

        definitions.put(symbol, new Entry(symbol, initVal));
    }

    public void kill(SourceCodeSymbol symbol, Value newDef) {
        final var info = getEntryOpt(symbol)
            .orElseThrow(() -> new RuntimeException("Variable " + symbol + "was undefined."));

        info.kill(newDef);
    }

    public void killOrNewDef(SourceCodeSymbol symbol, Value value) {
        if (hasDef(symbol)) {
            kill(symbol, value);
        } else {
            newDef(symbol, value);
        }
    }

    public Optional<Entry> getEntryOpt(SourceCodeSymbol symbol) {
        return Optional.ofNullable(definitions.get(symbol));
    }

    public Set<Map.Entry<SourceCodeSymbol, Entry>> getAllEntries() {
        return definitions.entrySet();
    }

    public static class Entry extends User {
        Entry(SourceCodeSymbol symbol, Value initVal) {
            super(IRType.VoidTy);
            super.setSymbol(symbol);
            super.addOperandCO(initVal);
            this.version = 0;

            initVal.setSymbol(symbol);
        }

        public Value getCurrDef() {
            return getOperand(0);
        }

        public void kill(Value newDef) {
            super.replaceOperandCO(0, newDef);
            this.version += 1;

            newDef.setSymbol(getSymbol());
        }

        public int getVersion() {
            return version;
        }

        private int version;
    }

    private final Map<SourceCodeSymbol, Entry> definitions = new HashMap<>();
}
