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

        final var target = args[0];
        final var inputStream = openInput(args[1]);
        final var outputStream = openOutput(args[2]);
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
