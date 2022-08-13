package pass.ir.loop;

import ir.Module;
import pass.ir.IRPass;
import utils.Log;

import java.util.stream.Collectors;

public interface LoopPass extends IRPass {
    void runOnLoop(CanonicalLoop loop);

    @Override
    default void runPass(Module module) {
        for (final var function : module.getNonExternalFunction()) {
            final var collector = new CollectLoopsAndMakeItCanonical();
            final var loops = CanonicalLoop.getAllLoopInPostOrder(collector.collect(function)).stream()
                // TODO: 干脆在生成的时候直接 drop 掉没有 unique exit 的循环好了
                // 这种循环没人权也没优化的
                .filter(CanonicalLoop::hasUniqueExit)
                // 同理, 反转的 do-while 循环也无优化机会的
                .filter(loop -> !loop.isRotated())
                .collect(Collectors.toList());

            Log.info("In loop pass %s count %d loops".formatted(this.getClass().getSimpleName(), loops.size()));

            // final var simplifier = new SimpilySSAForLoop();
            // loops.forEach(simplifier::runOnLoop);
            //
            loops.forEach(this::runOnLoop);
        }
    }
}
