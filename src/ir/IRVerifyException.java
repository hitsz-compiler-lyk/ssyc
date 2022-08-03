package ir;

public class IRVerifyException extends RuntimeException {
    public IRVerifyException(Value value, String message) {
        this(value, message, null);
    }

    public IRVerifyException(Value value, String message, Throwable cause) {
        super("%s (From: %s)".formatted(value.toString(), message), cause);
        this.value = value;
        this.message = message;
    }

    public Value getFailedValue() {
        return value;
    }

    public String getRawMessage() {
        return message;
    }

    public static class SelfReferenceException extends IRVerifyException {
        public SelfReferenceException(Value value) {
            super(value, "Cannot use itself as an operand");
        }
    }

    private final Value value;
    private final String message;
}
