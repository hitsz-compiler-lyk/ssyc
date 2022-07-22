package top.origami404.ssyc.pass.ir;

import top.origami404.ssyc.ir.Function;
import top.origami404.ssyc.ir.Module;

import java.util.function.BooleanSupplier;

public class IRPassManager {
    public static void run(Module module) {
        for (final var func : module.getFunctions()) {
            if (func.isExternal()) continue;

            new Runner() {
                @Override public void run() {
                    flag = simpleClearAll(func) || flag;
                    flag = FunctionInline.run(func) || flag;
                }
            }.runUntilFalse();
        }

    }

    static boolean simpleClearAll(Function func) {
        new Runner() {
            @Override public void run() {
                flag = InstructionClear.clearAll(func)          || flag;
                flag = BlockClear.clearUnreachableBlock(func)   || flag;
                flag = InstructionClear.clearAll(func)          || flag;
                flag = BlockClear.mergeBlock(func)              || flag;
                flag = InstructionClear.clearAll(func)          || flag;
            }
        }.runUntilFalse();
        return false;
    }


    static abstract class Runner {
        public abstract void run();

        public void runUntilFalse() {
            do {
                flag = false;
                run();
            } while (flag);
        }

        protected boolean flag = false;
    }
}
