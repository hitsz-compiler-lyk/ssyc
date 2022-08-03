package utils;

import java.io.PrintStream;

public class Log {
    public static void info(String message) {
        out.println(makeFormattedOutput("info", message, colorYellow));
    }

    public static void debug(String message) {
        if (!Log.closeDebug) {
            out.println(makeFormattedOutput("debug", message, colorWhite));
        }
    }

    public static void ensure(boolean cond) {
        ensure(cond, "");
    }

    public static void ensure(boolean cond, String message) {
        if (cond) {
            return;
        }

        throw new AssertionError(makeFormattedOutput("assert", message, colorRed));
    }

    public static void inOnlineJudge() {
        Log.needColor = false;
        Log.closeDebug = true;
    }

    private static String makeFormattedOutput(String level, String message, String color) {
        // From: https://stackoverflow.com/a/31128774
        // 0 -- getStackTrace
        // 1 -- ensure (curr method)
        // 2 -- the wrapper (info/debug)
        // 3 -- the caller
        final var callerStackTrace = Thread.currentThread().getStackTrace()[3];
        final var lineNo = callerStackTrace.getLineNumber();
        final var filename = callerStackTrace.getFileName();
        final var basename = filename.replace(".java", "");

        return color + "[%5s][%25s:%4d] | %s".formatted(level, basename, lineNo, message) + colorNormal;
    }

    private static boolean needColor = true;
    private static boolean closeDebug = false;

    private static final String colorRed    = needColor ? "\033[31;1m" : "";
    private static final String colorYellow = needColor ? "\033[33;1m" : "";
    private static final String colorWhite  = needColor ? "\033[37;1m" : "";
    private static final String colorNormal = needColor ? "\033[0m"    : "";

    private static PrintStream out = System.err;
}
