package top.origami404.ssyc.utils;

import java.io.PrintStream;

public class Log {
    public static void info(String message) {
        out.println(makeFormattedOutput("info", message));
    }

    public static void debug(String message) {
        out.println(makeFormattedOutput("debug", message));
    }

    public static void ensure(boolean cond) {
        ensure(cond, "");
    }

    public static void ensure(boolean cond, String message) {
        if (cond) {
            return;
        }

        throw new AssertionError(makeFormattedOutput("assert", message));
    }

    private static String makeFormattedOutput(String level, String message) {
        // From: https://stackoverflow.com/a/31128774
        // 0 -- getStackTrace
        // 1 -- ensure (curr method)
        // 2 -- the wrapper (info/debug)
        // 3 -- the caller
        final var callerStackTrace = Thread.currentThread().getStackTrace()[3];
        final var lineNo = callerStackTrace.getLineNumber();
        final var filename = callerStackTrace.getFileName();
        final var basename = filename.replace(".java", "");

        return "[%5s][%15s:%4d] | %s".formatted(level, basename, lineNo, message);
    }

    private static PrintStream out = System.err;
}
