import utils.Log;

import java.io.IOException;

public class FunctionalProfile {
    public static void main(String[] args) throws IOException {
        final var basePath = "./test-data/hidden_functional/";
        final var file_name = "29_long_line";

        final var input = basePath + file_name + ".sy";
        final var output = basePath + file_name + ".llvm";

        Log.inOnlineJudge();
        Main.runWithLargeStack("asm", input, output);
    }
}
