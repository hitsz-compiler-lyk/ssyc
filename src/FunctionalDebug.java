import java.io.IOException;

public class FunctionalDebug {
    public static void main(String[] args) throws IOException {
        // final var basePath = "./test-data/functional/";
        // final var file_name = "21_if_test2";
        // final var basePath = "./test-data/functional/";
        final var basePath = "./test-data/personal/";
        final var file_name = "05-induction";

        final var input = basePath + file_name + ".sy";
        final var output = basePath + file_name + ".llvm";

        Main.runWithLargeStack("llvm", input, output);
    }
}
