import backend.regallocator.RegAllocManager;
import backend.codegen.ToAsmManager;
import backend.codegen.ToLIRManager;
import frontend.IRGen;
import frontend.SysYLexer;
import frontend.SysYParser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import pass.backend.BackendPassManager;
import pass.ir.IRPassManager;
import utils.LLVMDumper;
import utils.Log;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Main {
    public static boolean needOptimize = false;

    public static void main(String[] args) {
        var targetArgs = new ArrayList<String>();
        var flagArgs = new ArrayList<String>();
        for (var arg : args) {
            if (arg.startsWith("--")) {
                flagArgs.add(arg.substring(2));
            } else if (arg.startsWith("-") && !arg.equals("-")) {
                flagArgs.add(arg.substring(1));
            } else {
                targetArgs.add(arg);
            }
        }

        if (targetArgs.size() < 3) {
            System.err.println("Usage: ssyc <target> <input_file> <output_file>");
            throw new RuntimeException("Argument error: [" + String.join(" ", args) + "]");
        }

        parseFlag(flagArgs);
        runWithLargeStack(targetArgs.get(0), targetArgs.get(1), targetArgs.get(2));
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

            final var exceptionSaver = new ExceptionSaver();
            thread.setUncaughtExceptionHandler((t, exception) -> exceptionSaver.setException(exception));

            thread.start();
            thread.join();

            if (exceptionSaver.hasException()) {
                final var exception = exceptionSaver.getException();
                if (exception instanceof RuntimeException) {
                    throw (RuntimeException) exception;
                } else {
                    throw new RuntimeException("Exception in thread", exceptionSaver.getException());
                }
            }

        } catch (InterruptedException e) {
            throw new RuntimeException("Exception in thread", e);
        }
    }

    static class ExceptionSaver {
        public Throwable getException() {
            return exception;
        }

        public void setException(final Throwable exception) {
            this.exception = exception;
        }

        public boolean hasException() {
            return exception != null;
        }

        private Throwable exception = null;
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

                if (needOptimize) {
                    final var mgr = new IRPassManager(module);
                    mgr.runAllPasses();
                    module.verifyAll();
                }

                final var dumper = new LLVMDumper(outputStream);
                dumper.dump(module);
                dumper.close();
            }

            case "asm" -> {
                final var irGen = new IRGen();
                final var module = irGen.visitCompUnit(ruleContext);
                module.verifyAll();

                if (needOptimize) {
                    final var mgr = new IRPassManager(module);
                    mgr.runAllPasses();
                    module.verifyAll();
                }

                final var toLIRMgr = new ToLIRManager(module);
                final var armModule = toLIRMgr.codeGenLIR();

                final var regAllocMgr = new RegAllocManager(armModule);
                regAllocMgr.regAllocate();

                if (needOptimize) {
                    final var BackendPass = new BackendPassManager(armModule);
                    BackendPass.runAllPasses();
                }

                final var asm = new ToAsmManager(armModule).codeGenArm();
                writer.append(asm);
                writer.close();
            }

            default -> throw new RuntimeException("Unsupported target");
        }

        inputStream.close();
        outputStream.close();
    }

    private static void parseFlag(List<String> flags) {
        var needLog = false;
        for (var flag : flags) {
            if (flag.toLowerCase().startsWith("o")) {
                try {
                    var level = Integer.parseInt(flag.substring(1));
                    if (level >= 1) needOptimize = true;
                } catch (NumberFormatException ignored) {
                }
            } else if (flag.equalsIgnoreCase("log") || flag.equalsIgnoreCase("l")) {
                needLog = true;
            }
        }
        if (!needLog) Log.inOnlineJudge();
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
