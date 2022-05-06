package top.origami404.ssyc.frontend;

import top.origami404.ssyc.frontend.SysYParser.*;
import top.origami404.ssyc.ir.arg.*;
import top.origami404.ssyc.ir.inst.*;

/**
 * IRGenVisitor
 */
public class IRGen extends SysYBaseListener {
    @Override
    public void enterCompUnit(CompUnitContext ctx) {
        inGlobal = true;
    }

    @Override
    public void enterDecl(DeclContext ctx) {
        // 先不做全局变量
        if (inGlobal) {
            throw new RuntimeException("Unimpl");
        }
    }

    @Override
    public void enterFuncDef(FuncDefContext ctx) {
        inGlobal = false;
        currFunc = new Function(ctx.Ident().getText());
        currBlock = currFunc.getBlock("entry");

    }

    private void insert(Inst inst) {
        currBlock.insertInst(inst);
    }

    private boolean inGlobal;
    private Function currFunc;
    private BBlock currBlock;
}