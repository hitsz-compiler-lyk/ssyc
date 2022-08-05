package pass.ir.loop;

import ir.BasicBlock;
import ir.Module;
import ir.inst.BrInst;
import ir.inst.CallInst;
import ir.inst.Instruction;
import ir.inst.MemInitInst;
import pass.ir.IRPass;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class MoveLoopInvariant implements IRPass {
    @Override
    public void runPass(final Module module) {

    }



    List<Instruction> collectInvariant(NaturalLoop loop) {
        loop.getHeader().phis().forEach(this::collectVariants);

        return loop.getBlocks().stream()
            .flatMap(List<Instruction>::stream)
            .filter(this::isInvariant)
            .collect(Collectors.toUnmodifiableList());
    }

    void moveInvariant(BasicBlock header, List<Instruction> invariants) {
        // 大体上架构变换如下图:
        /*
         *                                           │      │        │
         *                                        ┌──▼──────▼────────▼──┐
         *                                        │ Cond (header)       │ F
         *                                        │ (Phi for outside)   ├──────────┐
         *                                        └─────────┬───────────┘          │
         *                                                  │ T                    │
         *        │    │   │                      ┌─────────▼───────────┐          │
         *        │    │   │                      │ Pre-header          │          │
         *        │    │   │                      │(Place for invariant)│          │
         *     ┌──▼────▼───▼────┐                 └─────────┬───────────┘          │
         *     │                │                           │                      │
         * ┌───►  Cond (header) │ F               ┌─────────▼───────────┐          │
         * │   │                ├─┐    ======>    │ Pre-body            │          │
         * │   └───────┬────────┘ │    ======>    │ (Phi for body)      ◄────┐     │
         * │           │ T        │    ======>    └─────────┬───────────┘    │     │
         * │   ┌───────▼────────┐ │                         │                │     │
         * │   │                │ │               ┌─────────▼───────────┐    │     │
         * └───┤  Body...       │ │               │ Body...             │    │     │
         *     │                │ │               │                     │    │     │
         *     └────────────────┘ │               └─────────┬───────────┘    │     │
         *                        │                         │                │     │
         *                        │               ┌─────────▼───────────┐    │     │
         *                        ▼               │ Cond_2              │ T  │     │
         *                                        │ (Copy for cond)     ├────┘     │
         *                                        └─────────┬───────────┘          │
         *                                                  │ F                    │
         *                                        ┌─────────▼──────────┐           │
         *                                        │ Exit               │           │
         *                                        │ (unique exit point)◄───────────┘
         *                                        └─────────┬──────────┘
         *                                                  │
         *                                                  ▼
         */
        // 要注意的细节:
        //      1. 原 Cond 里的 phi 要 "分裂":
        //          来自外界的值要在 cond 里合一个 phi, 然后来自 body 的值要再在 pre-body 里合一次 phi
    }

    boolean isInvariant(Instruction instruction) {
        final var isVariant = variants.contains(instruction)
            || instruction instanceof MemInitInst
            || instruction instanceof CallInst
            || instruction instanceof BrInst;
        // 不使用 variant 的 Store 和 Load 是 invariant 的

        return !isVariant;
    }

    void collectVariants(Instruction instruction) {
        instruction.getUserList().stream()
            .filter(Instruction.class::isInstance).map(Instruction.class::cast)
            .forEach(this::collectVariants);

        variants.add(instruction);
    }

    private Set<Instruction> variants = new LinkedHashSet<>();
}
