import utils.Log;

public class Compiler {
    public static void main(String[] args)  {
        // 功能测试：compiler -S -o testcase.s testcase.sy
        // 性能测试：compiler -S -o testcase.s testcase.sy -O2
        try {
            final var outputFileName = args[2];
            final var inputFileName = args[3];
            final var needOptimize = args.length == 6;

            Log.inOnlineJudge();
            Main.main(new String[]{"asm", inputFileName, outputFileName});

        } catch (Exception e) {
            throw new RuntimeException("Fail at arg: [" + String.join(", ", args) + "]", e);
        }
    }
}
