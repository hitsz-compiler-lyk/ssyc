package utils;

import java.io.PrintStream;

public class Log {
    public static void info(String message) {
        if (!Log.notOutput)
        {
            out.println(makeFormattedOutput("info", message, colorYellow));
        }
    }

    public static void debug(String message) {
        if (!Log.notOutput) {
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

        final var info = ReflectiveTools.getCallerInfo();
        throw new LogFailException(message, info.getLineNo(), info.getBaseName());
    }

    public static void inOnlineJudge() {
        Log.needColor = false;
        Log.notOutput = true;
    }

    private static String makeFormattedOutput(String level, String message, String color) {
        final var info = ReflectiveTools.getSourceInfo(2); // 要越过 info/debug 函数的包装
        return color + "[%5s][%25s:%4d] | %s".formatted(level, info.getBaseName(), info.getLineNo(), message) + colorNormal;
    }

    private static boolean needColor = true;
    private static boolean notOutput = false;

    private static final String colorRed    = needColor ? "\033[31;1m" : "";
    private static final String colorYellow = needColor ? "\033[33;1m" : "";
    private static final String colorWhite  = needColor ? "\033[37;1m" : "";
    private static final String colorNormal = needColor ? "\033[0m"    : "";

    private static PrintStream out = System.err;
}
