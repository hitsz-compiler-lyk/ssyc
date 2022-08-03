package frontend;

import org.antlr.v4.runtime.ParserRuleContext;

public class SemanticException extends RuntimeException {
    public SemanticException(ParserRuleContext ctx, String message) {
        super(makeMessage(ctx, message));
    }

    public static class GenExpInGlobalException extends SemanticException {
        public GenExpInGlobalException(ParserRuleContext ctx) {
            super(ctx, "Trying to generate an expression in global");
        }
    }

    private static String makeMessage(ParserRuleContext ctx, String additionalMessage) {
        final var startTok = ctx.getStart();
        final var line = startTok.getLine();
        final var column = startTok.getStartIndex();
        final var filename = startTok.getTokenSource().getSourceName();

        return filename + ":" + line + ":" + column + ": " + additionalMessage;
    }
}
