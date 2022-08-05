package utils;

public class ReflectiveTools {
    public static class SourceInfo {
        public SourceInfo(int lineNo, String baseName) {
            this.lineNo = lineNo;
            this.baseName = baseName;
        }

        public int getLineNo() {
            return lineNo;
        }

        public String getBaseName() {
            return baseName;
        }

        public int lineNo;
        public String baseName;
    }

    /**
     * 获取源代码信息
     * @param layer 想要获取的函数相对于获取者在栈上高了几层
     */
    public static SourceInfo getSourceInfo(int layer) {
        // +2 是因为 getStackTrace, getSourceInfo 也要算
        final var callerStackTrace = Thread.currentThread().getStackTrace()[layer + 2];
        final var lineNo = callerStackTrace.getLineNumber();
        final var filename = callerStackTrace.getFileName();
        final var basename = filename.replace(".java", "");

        return new SourceInfo(lineNo, basename);
    }

    public static SourceInfo getCallerInfo() {
        // 是要获得 caller 的 caller Info, 所以要在自己的基础上往上两
        return getSourceInfo(2);
    }
}
