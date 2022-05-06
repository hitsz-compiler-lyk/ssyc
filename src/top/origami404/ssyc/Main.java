package top.origami404.ssyc;

import java.io.*;

import org.antlr.v4.runtime.*;

import top.origami404.ssyc.frontend.*;
import top.origami404.ssyc.ir.Module;

public class Main {
    public static void main(String[] args) throws IOException, FileNotFoundException {
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
        final var tree = parser.compUnit();

        switch (target) {
            case "ast" -> {
                writer.write(tree.toStringTree());
                writer.write("\n");
            }

            case "ir" -> {
                final var visitor = new IRGen();
                final var module = (Module) visitor.visit(tree);

                writer.write(module.toTextForm());
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
