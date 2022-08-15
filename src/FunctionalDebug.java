import java.io.IOException;

public class FunctionalDebug {
    public static void main(String[] args) throws IOException {
        // final var basePath = "./test-data/functional/";
        // final var file_name = "21_if_test2";
        // final var basePath = "./test-data/functional/";
        final var basePath = "./test-data/hidden_functional/";
        final var file_name = "29_long_line";

        final var input = basePath + file_name + ".sy";
        final var output = basePath + file_name + ".llvm";

        Main.runWithLargeStack("asm", input, output);
    }
}
