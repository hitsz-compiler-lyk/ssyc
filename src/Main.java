import java.io.*;

import org.antlr.v4.runtime.*;

import backend.codegen.CodeGenManager;
import frontend.*;
import pass.ir.IRPassManager;
import utils.LLVMDumper;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.out.println("Usage: ssyc <target> <input_file> <output_file>");
            throw new RuntimeException("Argument error: [" + String.join(" ", args) + "]");
        }

        runWithLargeStack(args[0], args[1], args[2]);
    }

    static void runWithLargeStack(String target, String inputFileName, String outputFileName) {
        try {
            final var thread = new Thread(null, () -> {
                try {
                    Main.runNormal(target, inputFileName, outputFileName);
                } catch (IOException e) {
                    throw new RuntimeException("IO exception on main", e);
                }
            }, "", 1 << 30);

            thread.start();
            thread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException("Exception in thread", e);
        }
    }

    public static void runNormal(String target, String inputFileName, String outputFileName) throws IOException {
        final var inputStream = openInput(inputFileName);
        final var outputStream = openOutput(outputFileName);
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
                writer.close();
            }

            case "llvm" -> {
                final var irGen = new IRGen();
                final var module = irGen.visitCompUnit(ruleContext);
                module.verifyAll();

                final var mgr = new IRPassManager(module);
                mgr.runAllPasses();
                module.verifyAll();

                final var dumper = new LLVMDumper(outputStream);
                dumper.dump(module);
                dumper.close();
            }

            case "asm" -> {
                final var irGen = new IRGen();
                final var module = irGen.visitCompUnit(ruleContext);
                module.verifyAll();

                final var mgr = new IRPassManager(module);
                mgr.runAllPasses();
                module.verifyAll();

                final var codeGenManager = new CodeGenManager();
                codeGenManager.genArm(module);
                writer.append(codeGenManager.codeGenArm());
                writer.close();
            }

            default -> {
                throw new RuntimeException("Unsupport target");
            }
        }

        inputStream.close();
        outputStream.close();
    }

    private static InputStream openInput(String filename) throws FileNotFoundException {
        if (filename.equals("-")) {
            return System.in;
        }

        return new FileInputStream(filename);
    }

    private static OutputStream openOutput(String filename) throws IOException {
        if (filename.equals("-")) {
            return System.out;
        }

        final var file = new File(filename);
        file.createNewFile();
        return new FileOutputStream(file);
    }
}
