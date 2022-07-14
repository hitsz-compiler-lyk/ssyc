package top.origami404.ssyc;

import java.io.*;

import org.antlr.v4.runtime.*;

import top.origami404.ssyc.frontend.*;
import top.origami404.ssyc.utils.LLVMDumper;

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

                final var dumper = new LLVMDumper(outputStream);
                dumper.dump(module);
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
