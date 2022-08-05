package ir;

import utils.ReflectiveTools;

public class IRVerifyException extends RuntimeException {
    public static IRVerifyException create(Value value, String message, Throwable cause) {
        final var info = ReflectiveTools.getCallerInfo();
        return new IRVerifyException(info.getLineNo(), value, message, cause);
    }

    public static IRVerifyException create(Value value, String message) {
        final var info = ReflectiveTools.getCallerInfo();
        return new IRVerifyException(info.getLineNo(), value, message, null);
    }

    public static IRVerifyException create(int lineNo, Value value, String message) {
        return new IRVerifyException(lineNo, value, message, null);
    }

    private IRVerifyException(int lineNo, Value value, String message, Throwable cause) {
        super("%s (From: %s) (%d)".formatted(value.toString(), message, lineNo), cause);
        this.lineNo = lineNo;
        this.value = value;
        this.message = message;
    }

    public int getLineNo() {
        return lineNo;
    }

    public Value getFailedValue() {
        return value;
    }

    public String getRawMessage() {
        return message;
    }

    public static class SelfReferenceException extends IRVerifyException {
        public static SelfReferenceException create(Value value) {
            final var info = ReflectiveTools.getCallerInfo();
            return new SelfReferenceException(info.getLineNo(), value);
        }

        private SelfReferenceException(int lineNo, Value value) {
            super(lineNo, value, "Cannot use itself as an operand", null);
        }
    }

    private final int lineNo;
    private final Value value;
    private final String message;
}
