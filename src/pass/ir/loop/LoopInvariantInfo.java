package pass.ir.loop;

import ir.GlobalVar;
import ir.Value;
import ir.inst.Instruction;
import ir.inst.LoadInst;
import ir.inst.MemInitInst;

import java.util.LinkedHashSet;
import java.util.Set;

public class LoopInvariantInfo {
    public static LoopInvariantInfo collect(CanonicalLoop loop) {
        return new LoopInvariantInfo(loop);
    }

    public boolean isInvariant(Value value) {
        return !isVariants(value);
    }

    public boolean isVariants(Value value) {
        if (value instanceof Instruction) {
            // 对全局数组的 load 不应该算是 variant 的
            // 但是对全局变量的 load 就有可能是了
            if (value instanceof LoadInst) {
                final var ptr = ((LoadInst) value).getPtr();
                return !(ptr instanceof GlobalVar && ((GlobalVar) ptr).isArray());
            } else {
                return variants.contains((Instruction) value);
            }
        } else {
            // 非指令以外的 Value 必然都是循环无关的
            return false;
        }
    }

    public CanonicalLoop getLoop() {
        return loop;
    }

    private void collectVariants() {
        // phis in header are variants
        // use of variants are variants
        loop.getHeader().phis().forEach(this::collectAsVariantsWithAllUser);

        for (final var block : loop.getBody()) {
            for (final var inst : block) {
                if (inst instanceof MemInitInst) {
                    variants.add(inst);
                } else if (inst.getKind().isBr()) {
                    variants.add(inst);
                }
                // 不使用 variant 的 Store 和 Load 是 invariant 的
            }
        }

        // 反之, 如果一个内存位置被 Store 了, 它的所有 Load 都是 variant 的
        // TODO: 思考如何实现?
        // 总之先暴力假设 Load 跟 Store 都是有关的
    }

    private void collectAsVariantsWithAllUser(Instruction instruction) {
        if (variants.contains(instruction)) {
            return;
        }

        variants.add(instruction);

        instruction.getUserList().stream()
            .filter(Instruction.class::isInstance).map(Instruction.class::cast)
            .forEach(this::collectAsVariantsWithAllUser);
    }

    private LoopInvariantInfo(CanonicalLoop loop) {
        this.loop = loop;
        this.variants = new LinkedHashSet<>();

        this.collectVariants();
    }

    private final CanonicalLoop loop;
    private final Set<Instruction> variants;
}
