import java.io.IOException;

public class FunctionalDebug {
    public static void main(String[] args) throws IOException {
        final var basePath = "./test-data/functional/";
        final var file_name = "80_chaos_token";

        final var input = basePath + file_name + ".sy";
        final var output = basePath + file_name + ".llvm";

        Main.main(new String[]{"llvm", input, output});
    }
}
