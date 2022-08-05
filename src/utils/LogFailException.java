package utils;

public class LogFailException extends RuntimeException {
    public LogFailException(String message, int lineNo, String baseName) {
        super("%s (at %s:%d)".formatted(message, baseName, lineNo));
        this.lineNo = lineNo;
        this.baseName = baseName;
    }

    public int getLineNo() {
        return lineNo;
    }

    public String getBaseName() {
        return baseName;
    }

    private final int lineNo;
    private final String baseName;
}
