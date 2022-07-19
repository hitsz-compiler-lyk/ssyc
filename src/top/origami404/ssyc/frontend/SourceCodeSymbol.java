package top.origami404.ssyc.frontend;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * 代表了源语言里的一个特定的变量
 */
public class SourceCodeSymbol {
    private final String name;
    private final int line;
    private final int column;

    public SourceCodeSymbol(TerminalNode terminalNode) {
        this(terminalNode.getSymbol());
    }

    public SourceCodeSymbol(Token token) {
        this(token.getText(), token);
    }

    public SourceCodeSymbol(String name, ParserRuleContext ctx) {
        this(name, ctx.getStart());
    }

    public SourceCodeSymbol(String name, Token token) {
        this(name, token.getLine(), token.getStartIndex());
    }

    public SourceCodeSymbol(String name, int line, int column) {
        this.name = name;
        this.line = line;
        this.column = column;
    }

    /**
     * @return "名字:行号:列号"
     */
    public String getVSCodeDescriptor() {
        return "%s:%d:%d".formatted(name, line, column);
    }

    /**
     * @return "%名字$行号$列号"
     */
    public String getLLVMLocal() {
        return "%%%s$%d$%d".formatted(name, line, column);
    }

    /**
     * @return "@名字$行号$列号"
     */
    public String getLLVMGlobal() {
        return "@%s$%d$%d".formatted(name, line, column);
    }

    public String getName() {
        return name;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    @Override
    public String toString() {
        return getVSCodeDescriptor();
    }

    @Override
    public int hashCode() {
        var result = 0;
        result = 37 * result + name.hashCode();
        result = 37 * result + line;
        result = 37 * result + column;
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof SourceCodeSymbol) {
            final var other = (SourceCodeSymbol) obj;
            return line == other.line && column == other.column && name.equals(other.name);
        } else {
            return false;
        }
    }
}
