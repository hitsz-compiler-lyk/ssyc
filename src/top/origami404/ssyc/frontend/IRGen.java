package top.origami404.ssyc.frontend;

import top.origami404.ssyc.frontend.SysYParser.*;
import top.origami404.ssyc.ir.arg.*;
import top.origami404.ssyc.ir.inst.*;

/**
 * IRGenVisitor
 */
public class IRGen extends SysYBaseVisitor<Object> {
    @Override
    public Module visitCompUnit(CompUnitContext ctx) {
        inGlobal = true;
    }

    @Override
    public Object visitDecl(DeclContext ctx) {
        if (inGlobal) {
            throw new RuntimeException("Unimpl");
        }
    }

    @Override
    public Void visitFuncDef(FuncDefContext ctx) {
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