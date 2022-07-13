package top.origami404.ssyc;

import java.io.*;

import org.antlr.v4.runtime.*;

import top.origami404.ssyc.frontend.*;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 4) {
            System.out.println("Usage: ssyc <target> <input_file> <output_file>");
        }

        final var target = args[1];
        final var inputStream = openInput(args[2]);
        final var outputStream = openOutput(args[3]);
        final var writer = new OutputStreamWriter(outputStream);

        final var input = CharStreams.fromStream(inputStream);
        final var lexer = new SysYLexer(input);
        final var tokens = new CommonTokenStream(lexer);
        final var parser = new SysYParser(tokens);
        final var ruleContext = parser.compUnit();

        switch (target) {
            case "ast" -> {
                writer.write(DebugTools.toDebugTreeString(ruleContext).toString());
                writer.write("\n");
            }

            case "llvm" -> {
                final var irGen = new IRGen();
                final var module = irGen.visitCompUnit(ruleContext);
                module.verifyAll();
                // TODO: llvm ir dump
                writer.append("define dso_local i32 @main() #0 {\n");
                writer.append("  %1 = alloca i32, align 4\n");
                writer.append("  store i32 0, i32* %1, align 4\n");
                writer.append("  ret i32 0\n");
                writer.append("}\n");
                writer.append("\n");
                writer.append("attributes #0 = { noinline nounwind optnone \"correctly-rounded-divide-sqrt-fp-math\"=\"false\" \"disable-tail-calls\"=\"false\" \"frame-pointer\"=\"all\" \"less-precise-fpmad\"=\"false\" \"min-legal-vector-width\"=\"0\" \"no-infs-fp-math\"=\"false\" \"no-jump-tables\"=\"false\" \"no-nans-fp-math\"=\"false\" \"no-signed-zeros-fp-math\"=\"false\" \"no-trapping-math\"=\"true\" \"stack-protector-buffer-size\"=\"8\" \"target-cpu\"=\"arm7tdmi\" \"target-features\"=\"+armv4t,+strict-align,-thumb-mode\" \"unsafe-fp-math\"=\"false\" \"use-soft-float\"=\"false\" }\n");
            }

            case "asm" -> {
                writer.append(".global main\n");
                writer.append(".func main\n");
                writer.append("main:\n");
                writer.append("    mov r0, #1\n");
                writer.append("    bx lr\n");
            }

            default -> {
                throw new RuntimeException("Unsupport target");
            }
        }

        writer.close();
    }

    private static InputStream openInput(String filename) throws FileNotFoundException {
        if (filename.equals("-")) {
            return System.in;
        }

        return new FileInputStream(filename);
    }

    private static OutputStream openOutput(String filename) throws FileNotFoundException {
        if (filename.equals("-")) {
            return System.out;
        }

        return new FileOutputStream(filename);
    }
}
