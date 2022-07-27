package top.origami404.ssyc.frontend.info;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.analysis.AnalysisInfo;
import top.origami404.ssyc.ir.constant.FloatConst;
import top.origami404.ssyc.ir.constant.IntConst;
import top.origami404.ssyc.ir.inst.InstKind;
import top.origami404.ssyc.ir.inst.Instruction;

public class InstCache implements AnalysisInfo {
    public Optional<Instruction> get(InstKind kind, List<Value> operands) {
        final var hash = calcHashCode(kind, operands);
        return Optional.ofNullable(cache.get(hash));
    }

    public Instruction getOrSelf(Instruction inst) {
        return this.get(inst.getKind(), inst.getOperands())
            .orElse(inst);
    }

    public Instruction getOrAdd(Instruction inst) {
        if (!contains(inst)) {
            add(inst);
        }

        return get(inst.getKind(), inst.getOperands()).orElseThrow();
    }

    public void addOrReplace(Instruction inst) {
        if (!contains(inst)) {
            add(inst);
        } else {
            final var inCache = get(inst.getKind(), inst.getOperands()).orElseThrow();
            if (inCache != inst) {
                inst.replaceInIList(inCache);
                inst.replaceAllUseWith(inCache);
                inst.freeFromUseDef();
            }
        }
    }

    void add(Instruction newInst) {
        final var kind = newInst.getKind();
        final var operands = newInst.getOperands();

        final var hash = calcHashCode(kind, operands);
        if (cache.containsKey(hash)) {
            throw new RuntimeException("Entry has already in cache");
        } else {
            cache.put(hash, newInst);
        }
    }

    boolean contains(Instruction inst) {
        return contains(inst.getKind(), inst.getOperands());
    }

    boolean contains(InstKind kind, List<Value> operands) {
        final var hash = calcHashCode(kind, operands);
        return cache.containsKey(hash);
    }

    /**
     * 根据 kind 与 operands 计算一个哈希值以供参考
     * 哈希方法来自:  <a href="https://stackoverflow.com/a/113600">SO 回答</a>
     * @param kind 指令的种类
     * @param operands 指令的参数
     * @return 哈希值
     */
    private int calcHashCode(InstKind kind, List<Value> operands) {
        // 使用 stream API 的话, 会导致 int 反复装箱开箱, 可能会导致性能问题
        int hash = kind.ordinal();

        for (final var op : operands) {
            hash *= 37;
            if (op instanceof IntConst) {
                hash += ((IntConst) op).getValue();
            } else if (op instanceof FloatConst) {
                hash += Float.floatToIntBits(((FloatConst) op).getValue());
            } else {
                // 在 Cache 里的 Inst 的参数应该也在 Cache 里
                // 所以 operands 里的 op 应该都是唯一的
                // 直接使用 identifyHashCode
                hash += System.identityHashCode(op);
            }
        }

        return hash;
    }

    // TODO: 衡量反复装箱 int 导致的性能问题
    private HashMap<Integer, Instruction> cache = new HashMap<>();
}
