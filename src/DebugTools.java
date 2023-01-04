import frontend.SysYParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;

public class DebugTools {
    public static String findFirstToken(ParserRuleContext rc) {
        if (rc.children == null)
            return "@";

        for (final var child : rc.children) {
            if (child instanceof final TerminalNode tn) {
                return tn.getText();
            }
        }

        return "#";
    }

    public static StringBuffer toDebugTreeString(ParserRuleContext rc) {
        final var sb = new StringBuffer();
        sb.append('(');

        sb.append(SysYParser.ruleNames[rc.getRuleIndex()]);
        sb.append('['); sb.append(findFirstToken(rc)); sb.append(']');

        for (final var child : rc.getRuleContexts(ParserRuleContext.class)) {
            sb.append(' ');
            sb.append(toDebugTreeString(child));
        }

        sb.append(')');
        return sb;
    }

    private DebugTools() {}
}
