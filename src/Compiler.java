import ir.IRVerifyException;
import utils.Log;
import utils.LogFailException;

import java.util.Objects;

public class Compiler {
    public static void main(String[] args) {
        // 功能测试：compiler -S -o testcase.s testcase.sy
        // 性能测试：compiler -S -o testcase.s testcase.sy -O2
        try {
            final var outputFileName = args[2];
            final var inputFileName = args[3];
            var needOptimize = false;
            var needLog = false;
            for (var arg : args) {
                if (arg.equalsIgnoreCase("-o2")) {
                    needOptimize = true;
                }
                if (arg.equalsIgnoreCase("-log")) {
                    needLog = true;
                }
            }

            if (!needLog) {
                Log.inOnlineJudge();
            }
            Main.needOptimize = needOptimize;
            Main.runWithLargeStack("asm", inputFileName, outputFileName);

        } catch (LogFailException | IRVerifyException e) {
            e.printStackTrace();
            System.exit(-1);
        } catch (Exception e) {
            throw new RuntimeException("Fail at arg: [" + String.join(", ", args) + "]", e);
        }
    }


}
