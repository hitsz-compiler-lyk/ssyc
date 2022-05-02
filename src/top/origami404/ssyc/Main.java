package top.origami404.ssyc;

import java.io.*;

import org.antlr.v4.runtime.*;
import top.origami404.ssyc.frontend.*;

public class Main {
    public static void main(String[] args) throws IOException, FileNotFoundException {
        if (args.length != 4) {
            System.out.println("Usage: ssyc <target> <input_file> <output_file>");
        }

        final var target = args[1];
        var inputStream = openInput(args[2]);
        var outputStream = openOutput(args[3]);
        var writer = new OutputStreamWriter(outputStream);

        switch (target) {
            case "ast" -> {
                var input = CharStreams.fromStream(inputStream);
                var lexer = new SysYLexer(input);
                var tokens = new CommonTokenStream(lexer);
                var parser = new SysYParser(tokens);
                var tree = parser.compUnit();
                writer.write(tree.toStringTree());
                writer.write("\n");
            }

            case "ir" -> {
                throw new RuntimeException("Unsupport ir yet.");
            }

            case "asm" -> {
                writer.append(".global main\n");
                writer.append(".func main\n");
                writer.append("main:\n");
                writer.append("    mov r0, #0\n");
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
