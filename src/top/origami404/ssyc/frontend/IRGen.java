package top.origami404.ssyc.frontend;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import top.origami404.ssyc.frontend.SysYParser.*;
import top.origami404.ssyc.ir.Module;
import top.origami404.ssyc.ir.arg.*;
import top.origami404.ssyc.ir.inst.*;
import top.origami404.ssyc.ir.type.*;

/**
 * IRGenVisitor
 */
public class IRGen extends SysYBaseVisitor<Object> {
    public IRGen() {
        this.currSymTab = new SymTab();

        this.currFunc = null;
        this.currBlock = null;
    }

    @Override
    public Module visitCompUnit(CompUnitContext ctx) {
        inGlobal = true;
        return null;
    }

    @Override
    public Object visitDecl(DeclContext ctx) {
        if (inGlobal) {
            throw new RuntimeException("Unimplemented");
        }

        return null;
    }

    @Override
    public Void visitFuncDef(FuncDefContext ctx) {
        inGlobal = false;

        currSymTab = new SymTab(currSymTab);
        final var paramTypeList = visitFuncParamList(ctx.funcParamList());
        final var returnType = BType.toBType(ctx.BType().getText());
        final var type = new FuncType(paramTypeList, returnType);

        final var funcName = ctx.Ident().getText();

        currSymTab.getParent().ifPresent(tab -> tab.put(funcName, type));
        currFunc = new Function(funcName, type);
        currBlock = currFunc.getBlock("entry");

        visit(ctx.block());

        currFunc.insertReturn();

        inGlobal = true;
        return null;
    }

    @Override
    public List<Type> visitFuncParamList(FuncParamListContext ctx) {
        return ctx.funcParam().stream()
            .map(this::visitFuncParam)
            .collect(Collectors.toList());
    }

    @Override
    public Type visitFuncParam(FuncParamContext ctx) {
        final var baseType = BType.toBType(ctx.BType().getText());
        final var lval = visitLValDecl(ctx.lValDecl());
        final var type = ArrayType.toArrayType(baseType, lval.sizes);

        currSymTab.put(lval.name, type);
        return type;
    }

    class LValDeclResult {
        public final String name;
        public final List<Integer> sizes;

        public LValDeclResult(String name, List<Integer> sizes) {
            this.name = name;
            this.sizes = sizes;
        }
    }

    @Override
    public LValDeclResult visitLValDecl(LValDeclContext ctx) {
        final var name = ctx.Ident().getText();

        final var sizes = new ArrayList<Integer>();
        if (ctx.emptyDim() != null) {
            sizes.add(0);
        }

        inConstExp = true;
        final var rest = ctx.constExp().stream()
            .map(this::visitConstExp)
            .collect(Collectors.toList());
        inConstExp = false;

        sizes.addAll(rest);

        return new LValDeclResult(name, sizes);
    }

    @Override
    public Integer visitConstExp(ConstExpContext ctx) {
        inConstExp = true;
        final var result = (Integer) visitExp(ctx.exp());
        assert result != null;
        inConstExp = false;

        return result;
    }

    @Override
    public Object visitExp(ExpContext ctx) {
        return visitExpAdd(ctx.expAdd());
    }

    @Override
    public Object visitExpAdd(ExpAddContext ctx) {
        if (inConstExp) {
            final var left = (Integer) visitExpMul(ctx.expMul());
            final var right = (Integer) visitExpAdd(ctx.expAdd());
            final var op = ctx.ExpAddOp().getText();
            return switch (op) {
                case "+" -> left + right;
                case "-" -> left - right;
                default -> throw new RuntimeException("Illgeal operator: " + op);
            };
        }

        return null;
    }

    @Override
    public Object visitExpMul(ExpMulContext ctx) {
        if (inConstExp) {
            final var left = (Integer) visitExpUnary(ctx.expUnary());
            final var right = (Integer) visitExpMul(ctx.expMul());
            final var op = ctx.ExpMulOp().getText();
            return switch (op) {
                case "*" -> left * right;
                case "/" -> left / right;
                case "%" -> left % right;
                default -> throw new RuntimeException("Illgeal operator: " + op);
            };
        }

        return null;
    }

    @Override
    public Object visitExpUnary(ExpUnaryContext ctx) {
        if (inConstExp) {
            final var op = ctx.ExpUnaryOp().getText();
            final var operand = (Integer) visitExpUnary(ctx.expUnary());
            return switch (op) {
                case "-" -> -operand;
                case "+" -> +operand;
                // case ! -> throw
                default -> throw new RuntimeException("Illgeal operator: " + op);
            };
        }

        return null;
    }

    private void insert(Inst inst) {
        currBlock.insertInst(inst);
    }

    private boolean inGlobal;
    private boolean inConstExp;
    private Function currFunc;
    private BBlock currBlock;
    private SymTab currSymTab;
}