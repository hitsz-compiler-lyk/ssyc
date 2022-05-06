package top.origami404.ssyc.frontend;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import top.origami404.ssyc.frontend.SysYParser.*;
import top.origami404.ssyc.ir.Module;
import top.origami404.ssyc.ir.arg.*;
import top.origami404.ssyc.ir.inst.*;
import top.origami404.ssyc.ir.type.*;
import top.origami404.ssyc.misc.ChainMap;

/**
 * IRGenVisitor
 */
public class IRGen extends SysYBaseVisitor<Object> {
    public IRGen() {
        this.currTypeTab = new ChainMap<>();
        this.currConstTab = new ChainMap<>();

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

        currTypeTab = new ChainMap<>(currTypeTab);
        final var paramTypeList = visitFuncParamList(ctx.funcParamList());
        final var returnType = BType.toBType(ctx.BType().getText());
        final var type = new FuncType(paramTypeList, returnType);

        final var funcName = ctx.Ident().getText();

        currTypeTab.getParent().ifPresent(tab -> tab.put(funcName, type));
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

        currTypeTab.put(lval.name, type);
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

        final var rest = ctx.constExp().stream()
            .map(this::visitConstExp)
            .collect(Collectors.toList());
        sizes.addAll(rest);

        return new LValDeclResult(name, sizes);
    }

    @Override
    public Integer visitConstExp(ConstExpContext ctx) {
        return visitConstExpAdd(ctx.constExpAdd());
    }

    @Override
    public Integer visitConstExpAdd(ConstExpAddContext ctx) {
        final var left = visitConstExpMul(ctx.constExpMul());
        if (ctx.constExpAdd() == null)
            return left;
        
        final var right = visitConstExpAdd(ctx.constExpAdd());
        final var op = ctx.ExpAddOp().getText();

        return switch (op) {
            case "+" -> left + right;
            case "-" -> left - right;
            default -> throw new RuntimeException("Illgeal operator: " + op);
        };
    }

    @Override
    public Integer visitConstExpMul(ConstExpMulContext ctx) {
        final var left = visitConstExpUnary(ctx.constExpUnary());
        if (ctx.constExpMul() == null)
            return left;
        
        final var right = visitConstExpMul(ctx.constExpMul());
        final var op = ctx.ExpMulOp().getText();

        return switch (op) {
            case "*" -> left * right;
            case "/" -> left / right;
            case "%" -> left % right;
            default -> throw new RuntimeException("Illgeal operator: " + op);
        };
    }

    @Override
    public Integer visitConstExpUnary(ConstExpUnaryContext ctx) {
        if (ctx.ExpUnaryOp() == null) {
            return visitConstExpAtom(ctx.constExpAtom());
        }

        final var op = ctx.ExpUnaryOp().getText();
        final var operand = visitConstExpUnary(ctx.constExpUnary());
        return switch (op) {
            case "-" -> -operand;
            case "+" -> +operand;
            // case ! -> throw
            default -> throw new RuntimeException("Illgeal operator: " + op);
        };
    }

    @Override
    public Integer visitConstExpAtom(ConstExpAtomContext ctx) {
        if (ctx.Ident() != null) {
            final var name = ctx.Ident().getText();
            return currConstTab.get(name)
                .orElseThrow(() -> new RuntimeException("Undefined constant: " + name));
        } else if (ctx.IntConst() != null) {
            return Integer.parseInt(ctx.IntConst().getText());
        } else {
            return visitConstExp(ctx.constExp());
        }
    }

    private void insert(Inst inst) {
        currBlock.insertInst(inst);
    }

    private boolean inGlobal;
    private Function currFunc;
    private BBlock currBlock;
    private ChainMap<String, Type> currTypeTab;
    private ChainMap<String, Integer> currConstTab;
}