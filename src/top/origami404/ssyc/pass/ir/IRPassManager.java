package top.origami404.ssyc.pass.ir;

import top.origami404.ssyc.ir.Function;
import top.origami404.ssyc.ir.Module;

import java.util.function.BooleanSupplier;

public class IRPassManager {
    public static void run(Module module) {
        for (final var func : module.getFunctions()) {
            if (func.isExternal()) continue;

            runUntilAllFalse(
                () -> simpleClearAll(func),
                IRPassManager::doNothing
            );
        }
    }

    static boolean simpleClearAll(Function func) {
        runUntilAllFalse(
            () -> InstructionClear.clearAll(func),
            () -> BlockClear.clearUnreachableBlock(func),
            () -> InstructionClear.clearAll(func),
            () -> BlockClear.mergeBlock(func),
            () -> InstructionClear.clearAll(func),
            IRPassManager::doNothing
        );

        return false;
    }

    static boolean doNothing() {
        return false;
    }

    static void runUntilAllFalse(BooleanSupplier... suppliers) {
        boolean flag;

        do {
            flag = false;
            for (final var supplier : suppliers) {
                // 注意短路, 参数顺序不要反了
                flag = supplier.getAsBoolean() || flag;
            }
        } while (flag);
    }
}
