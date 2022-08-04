import java.io.IOException;

public class FunctionalDebug {
    public static void main(String[] args) throws IOException {
        final var basePath = "./test-data/performance/";
        // final var basePath = "./test-data/functional/";
        final var file_name = "shuffle0";

        final var input = basePath + file_name + ".sy";
        final var output = basePath + file_name + ".llvm";

        Main.runWithLargeStack("llvm", input, output);
    }
}
