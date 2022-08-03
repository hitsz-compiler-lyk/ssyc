import top.origami404.ssyc.Main;

import java.io.IOException;

public class Compiler {
    public static void main(String[] args) throws IOException {
        // 功能测试：compiler -S -o testcase.s testcase.sy
        // 性能测试：compiler -S -o testcase.s testcase.sy -O2

        final var outputFileName = args[3];
        final var inputFileName = args[4];
        final var needOptimize = args.length == 6;

        Main.main(new String[]{"asm", inputFileName, outputFileName});
    }
}
