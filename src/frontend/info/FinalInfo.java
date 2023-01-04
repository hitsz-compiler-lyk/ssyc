package frontend.info;

import frontend.SourceCodeSymbol;
import ir.GlobalVar;
import ir.Parameter;
import ir.Value;
import ir.analysis.AnalysisInfo;
import ir.constant.Constant;
import ir.inst.CAllocInst;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

// 这里的 "常量" 并非指 IR 中的 "Constant";
// IR 中的 Constant 是指在编译期能立即得到值的东西 (本身绝大部分 IR 就已经都是不可变的了)
// 而这里的 "常量" 是指源代码中那些跟 IR 的绑定关系不会改变的 "变量"
// 命名为 Final Variable 是借用了 Java 中 final 关键字的语义
// 或者说, 这些源代码中的变量在编译期就能立即知道它的 "值", 这里的 "值" 指 IR 而不是对应的外部表示
// 只要绑定关系不变就认为是 SrcFinal, 而无论其绑定的 IR 是不是一个 Constant
// 当然, 对于在编译过程中就能确定的常量而言, 若其为普通类型且我们能确定它绑定不会变
// 那它必然是一个 int const 之类的, 绑定到 IR Constant 的东西
// 若其为数组类型, 那么它才有可能在绑定不变的情况下, 绑定到一个 Value (AllocInst)
public class FinalInfo implements AnalysisInfo {
    public void newDef(SourceCodeSymbol symbol, Value value) {
        if (hasDef(symbol)) {
            throw new RuntimeException("Cannot redefine a final");
        }

        value.setSymbol(symbol);
        finals.put(symbol, value);
    }

    public boolean hasDef(SourceCodeSymbol var) {
        return finals.containsKey(var);
    }

    public Optional<Constant> getNormalVar(SourceCodeSymbol var) {
        final var opt = getDefOpt(var);
        opt.ifPresent(v -> {
            if (!(v instanceof Constant)) {
                throw new RuntimeException("A normal final var must bind to a constant");
            }
        });
        return opt.map(Constant.class::cast);
    }

    public Optional<Value> getArrayVar(SourceCodeSymbol var) {
        final var opt = getDefOpt(var);
        opt.ifPresent(v -> {
            final var isCAlloc = v instanceof CAllocInst;
            final var isGlobalPtr = v instanceof GlobalVar && v.getType().isPtr();
            final var isParameter = v instanceof Parameter && v.getType().isPtr();

            if (!isCAlloc && !isGlobalPtr && !isParameter) {
                throw new RuntimeException(
                    "An array final var must bind to an CAllocInst or Global variable or a Parameter to a pointer");
            }
        });

        return opt;
    }

    public Optional<Value> getDefOpt(SourceCodeSymbol var) {
        return Optional.ofNullable(finals.get(var));
    }

    public Value getDef(SourceCodeSymbol symbol) {
        return getDefOpt(symbol).orElseThrow(() -> new RuntimeException("Symbol not found: " + symbol));
    }

    public Set<Entry<SourceCodeSymbol, Value>> getAllEntries() {
        return finals.entrySet();
    }

    private final Map<SourceCodeSymbol, Value> finals = new HashMap<>();
}
