package top.origami404.ssyc;

import org.antlr.runtime.*;
import org.antlr.runtime.tree.*;
import top.origami404.ssyc.frontend.*;
import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException, RecognitionException {
        var input = new ANTLRInputStream(System.in);
        var lexer = new SysYLexer(input);
        var tokens = new CommonTokenStream(lexer);
        var parser = new SysYParser(tokens);
        var tree = parser.compUnit().getTree();
        
        // int a, b, c[3], d[5]={2, 3, 4};
        for (var subtree : tree.getChildren()) {
            if (subtree instanceof CommonTree t) {
                System.out.println(t.toStringTree());
            } else {
                System.out.println("Not CommonTree");
            }
        }
    }
}
