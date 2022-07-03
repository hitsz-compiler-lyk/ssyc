package top.origami404.ssyc.frontend;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import top.origami404.ssyc.frontend.SemanticException.GenExpInGlobalException;
import top.origami404.ssyc.frontend.SysYParser.AtomContext;
import top.origami404.ssyc.frontend.SysYParser.AtomLValContext;
import top.origami404.ssyc.frontend.SysYParser.BlockContext;
import top.origami404.ssyc.frontend.SysYParser.CompUnitContext;
import top.origami404.ssyc.frontend.SysYParser.DeclContext;
import top.origami404.ssyc.frontend.SysYParser.DefContext;
import top.origami404.ssyc.frontend.SysYParser.ExpAddContext;
import top.origami404.ssyc.frontend.SysYParser.ExpContext;
import top.origami404.ssyc.frontend.SysYParser.ExpMulContext;
import top.origami404.ssyc.frontend.SysYParser.ExpUnaryContext;
import top.origami404.ssyc.frontend.SysYParser.FuncArgListContext;
import top.origami404.ssyc.frontend.SysYParser.FuncDefContext;
import top.origami404.ssyc.frontend.SysYParser.FuncParamContext;
import top.origami404.ssyc.frontend.SysYParser.FuncParamListContext;
import top.origami404.ssyc.frontend.SysYParser.InitValContext;
import top.origami404.ssyc.frontend.SysYParser.LValContext;
import top.origami404.ssyc.frontend.SysYParser.LValDeclContext;
import top.origami404.ssyc.frontend.SysYParser.StmtContext;
import top.origami404.ssyc.frontend.SysYParser.StmtIfContext;
import top.origami404.ssyc.frontend.SysYParser.StmtWhileContext;
import top.origami404.ssyc.frontend.info.FinalInfo;
import top.origami404.ssyc.frontend.info.VersionInfo;
import top.origami404.ssyc.frontend.info.VersionInfo.Variable;
import top.origami404.ssyc.ir.constant.ArrayConst;
import top.origami404.ssyc.ir.constant.Constant;
import top.origami404.ssyc.ir.constant.IntConst;
import top.origami404.ssyc.ir.inst.AllocInst;
import top.origami404.ssyc.ir.inst.GEPInst;
import top.origami404.ssyc.ir.BasicBlock;
import top.origami404.ssyc.ir.Parameter;
import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.Function;
import top.origami404.ssyc.ir.Module;
import top.origami404.ssyc.ir.type.IRType;
import top.origami404.ssyc.ir.type.SimpleIRTy;
import top.origami404.ssyc.utils.ChainMap;

public class IRGen extends SysYBaseVisitor<Object> {
    public IRGen() {
        super();
        this.scope = new ChainMap<>();
        this.builder = null;
        this.currModule = new Module();
    }

    @Override
    public Module visitCompUnit(CompUnitContext ctx) {
        currModule = new Module();
        ctx.children.forEach(this::visit);
        return currModule;
    }


//====================================================================================================================//


    //#region funcDef 函数定义相关
    @Override
    public Function visitFuncDef(FuncDefContext ctx) {
        final var returnType = toIRType(ctx.BType().getText());
        final var arguments = visitFuncParamList(ctx.funcParamList());
        final var function = new Function(returnType, arguments, ctx.Ident().getText());

        builder = new IRBuilder(function.getEntryBBlock());
        currModule.getFunctions().put(function.getName(), function);

        return function;
    }

    @Override
    public List<Parameter> visitFuncParamList(FuncParamListContext ctx) {
        return ctx.funcParam().stream().map(this::visitFuncParam).collect(Collectors.toList());
    }

    @Override
    public Parameter visitFuncParam(FuncParamContext ctx) {
        final var baseType = toIRType(ctx.BType().getText());
        final var info = visitLValDecl(ctx.lValDecl());
        final var type = createTypeForArgumentByShape(baseType, info.shape);

        return new Parameter(info.name, type);
    }

    static class LValInfo {
        public LValInfo(String name, List<Integer> shape) {
            this.name = name;
            this.shape = shape;
        }

        final String name;
        final List<Integer> shape;
    }

    @Override
    public LValInfo visitLValDecl(LValDeclContext ctx) {
        final var shape = new LinkedList<Integer>();

        if (ctx.emptyDim() != null) {
            shape.addFirst(-1);
        }

        for (final var exp : ctx.exp()) {
            final var num = visitExp(exp);
            if (num instanceof IntConst) {
            final var ic = (IntConst) num;
                shape.add(ic.getValue());
            } else {
                throw new RuntimeException("exp in lValExp must be an integer constant");
            }
        }

        return new LValInfo(ctx.Ident().getText(), shape);
    }
    //#endregion funcDef


//====================================================================================================================//


    //#region decl 变量声明相关 (包括初始化器)
    @Override
    public Void visitDecl(DeclContext ctx) {
        final var isConst = ctx.Const() != null;
        final var baseType = toIRType(ctx.BType().getText());

        ctx.def().forEach(d -> this.visitDef(d, isConst, baseType));
        return null;
    }

    @Override
    public Object visitDef(DefContext ctx) {
        throw new RuntimeException("Shouldn't be called");
    }

    public void visitDef(DefContext ctx, boolean isConst, IRType baseType) {
        final var info = visitLValDecl(ctx.lValDecl());
        final var name = info.name;
        final var shape = info.shape;
        final var type = createTypeByShape(baseType, shape);

        // 在当前作用域中注册该该名字为此变量
        final var variable = new Variable(name, ctx.getStart().getLine());
        final var originalVarOpt = scope.getInCurr(name);

        // 先检测可能的同作用域同名定义语义错误
        if (originalVarOpt.isPresent()) {
            throw new SemanticException(ctx,
                "Redefined identifier: %s, old at %d, new at %d"
                    .formatted(name, originalVarOpt.get().var.lineNo, variable.lineNo));
        }
        // 再放入
        scope.put(name, new ScopeEntry(variable, type));

        if (shape.isEmpty()) {
            // 对简单的变量, 只需要在有初始值的时候求值, 无初始值时默认零初始化即可
            final var value = Optional.ofNullable(ctx.initVal())
                .flatMap(n -> Optional.ofNullable(n.exp()))
                // 如果其初始化器需要运行时求值, 那么 visitExp 会将对应的求值指令插入到当前块中
                .map(this::visitExp)
                .orElse(Constant.getZeroByType(type));

            if (isConst && !(value instanceof Constant)) {
                throw new SemanticException(ctx, "Only constant can be used to initialize a constant variable");
            }

            newDefByFinalness(isConst, variable, value);

        } else {
            // 对数组变量, 需要特殊处理其初始值

            // 分配数组空间, 并且定义数组;
            // 数组变量的定义永远是指向对应空间的指针 (即那个 AllocInst), 并且永远不应该被重定义
            final var arrPtr = new AllocInst(type);
            newDefByFinalness(true, variable, arrPtr);

            // 为了确保常量初始化在 Store 之前, 必须先插入初始化指令, 后面再补上 init
            final var memInitInst = builder.insertMemInit(arrPtr);

            // 随后处理初始化器, 同样要考虑运行时求值的部分
            // 这里将初始化器处理为对应的 Value 的树状结构并补足末尾可能缺少的零元素
            // 对应位置的 Value 的求值语句将会在此过程中被插入
            final var initValue = visitInitVal(ctx.initVal(), isConst, baseType, shape);
            // 随后我们将不是 Constant 的 Value 替换为对应的零元素以获得可以放入 .text 段的常量数组
            // 然后插入对应的 GEP 指令与 Store 指令来将真正的值放到原本的位置
            // 全局数组只能使用常量来初始化, 所以不用担心全局数组的指令放哪
            final var init = makeArrayConstAndInsertStoreForNonConst(arrPtr, initValue);
            memInitInst.setInit(init);
        }
    }

    @Override
    public Void visitInitVal(InitValContext ctx) {
        throw new RuntimeException("Shouldn't be called");
    }

    public InitValue visitInitVal(InitValContext ctx, boolean isConst, IRType baseType, List<Integer> shape) {
        // TODO: 常量判断
        if (ctx == null) {
            return new InitExp(getZeroElm(baseType, shape));
        }

        return makeInitValue(ctx, 0, baseType, shape).value;
    }

    // 注意与 initVal 区分:
    // 本文件中提到 initVal 时指对应的语法上下文, 而提到 InitValue 时指下面的接口
    // 该接口表示 "初始化器对应的 **Value** 的树状结构"
    interface InitValue {}
    static class InitExp implements InitValue {
        public InitExp(Value exp) {
            this.exp = exp;
        }

        final Value exp;
    }

    static class InitArray implements InitValue {
        public InitArray(List<? extends InitValue> elms) {
            this.elms = elms;
        }

        final List<? extends InitValue> elms;
    }

    static class MakeInitValueResult {
        public MakeInitValueResult(int used, InitValue value) {
            this.used = used;
            this.value = value;
        }

        final int used;
        final InitValue value;
    }

    /**
     * 递归处理数组初始化器, 将其翻译为树状的, 完整的 (补上了 0 的) Value 列表
     * @param ctx 当前处理中的语法上下文
     * @param curr 目前处理到 ctx 的第几个 initVal
     * @param baseType 基本类型, 用于填充合适类型的零元素
     * @param shape 数组形状
     * @return 一个 record, 包含此次处理用去了 ctx 中的多少个元素以及处理后的 InitValue
     *
     * @see MakeInitValueResult
     * @see IRGen#visitInitVal(InitValContext, boolean, IRType, List)
     */
    private MakeInitValueResult makeInitValue(InitValContext ctx, int curr, IRType baseType, List<Integer> shape) {
        if (shape.isEmpty()) {
            // 递归边界: 处理只消耗单个类型为 exp 的 initVal 的情况
            final var target = ctx.initVal().get(curr);
            ensureInitValIsExp(target);

            try {
                final var value = new InitExp(visitExp(target.exp()));
                return new MakeInitValueResult(1, value); // 消耗了一个 ctx 中的元素, 返回 1

            } catch (GenExpInGlobalException e) {
                // 如果 visitExp 抱怨现在还在 global
                // 说明现在尝试在给全局变量使用非常量初始化
                // 而这是不允许的
                throw new SemanticException(ctx,
                    "Global value can only be initiated by constant variable, not even constant array");
            }
        }

        ensureInitValIsList(ctx);

        // 记录原本的 curr, 以在最后统计使用掉了多少个 initVal
        final var originalCurr = curr;
        // 最后获取到的子元素的 InitValue
        final var elms = new ArrayList<InitValue>();

        // 获得当前层数组应该有多少个元素
        // 随后尝试从 initVal 中获得这么多个子元素的 InitValue
        final var currDim = shape.get(0);
        for (int i = 0; i < currDim; i++) {
            if (curr >= ctx.initVal().size()) {
                // 如果已经到了 initVal 的末尾, 那么不移动 curr
                // 并且开始往 elms 里塞对应类型的零元素
                elms.add(new InitExp(getZeroElm(baseType, shape)));

            } else {
                // 若 initVal 还有元素, 就开始递归获取子元素的 InitValue
                final var target = ctx.initVal().get(curr);
                final var subShape = shape.subList(1, shape.size());

                if (target.exp() != null) {
                    // 如果该初始化器不是被大括号括起来的
                    // 那么接下来应该至少有 子元素所有元素数量 这么多个标量

                    // 我们保留当前的 ctx, 将目前的 curr 传递下去
                    // 正常情况下, 这样的递归将会一直递归到 shape 为 [], 触发边界情况
                    // 然后每一次递归的 for 循环里累计消耗了多少个元素, 最后累加回来
                    final var result = makeInitValue(ctx, curr, baseType, subShape);
                    // 更新目前在 ctx 的位置
                    curr += result.used;
                    elms.add(result.value);
                } else {
                    // 如果该初始化器是被大括号括起来的普通数组初始化器
                    // 就直接将该初始化器传下去; 因为是新的初始化器, 所以递归的 curr 参数是 0, 从头开始
                    final var result = makeInitValue(target, 0, baseType, subShape);
                    // 这种情况下 result.used 将会代表它消耗了 target 中的几个 initVal, 对 ctx 无意义
                    // 对 ctx 而言, 我们只消耗了一个 initVal (就是 target), 所以直接 +1
                    curr += 1;
                    elms.add(result.value);
                }
            }
        }

        assert elms.size() == currDim;

        // 稍作处理 & 包装, 返回上层需要的信息
        final var used = curr - originalCurr;
        final var value = new InitArray(elms);
        return new MakeInitValueResult(used, value);
    }

    private ArrayConst makeArrayConstAndInsertStoreForNonConst(Value arrPtr, InitValue initValue) {
        final var indices = new ArrayList<Integer>(); indices.add(0);
        final var constant = makeArrayConstAndInsertStoreForNonConstImpl(arrPtr, initValue, indices);

        if (constant instanceof ArrayConst) {
            return (ArrayConst) constant;
        } else {
            throw new RuntimeException("Shape don't match with initVal");
        }
    }

    /**
     * 将不是 Constant 的 Value 替换为对应的零元素; 插入对应的 GEP 指令与 Store 指令来将真正的值放到原本的位置
     * @param arrPtr 由 AllocInst 表示的, 指向数组的指针
     * @param initValue 之前生成的 "初始化器对应的 Value 的树状结构"
     * @param indices 将要传给 GEP 的索引, 注意递归开始时其中就得有一个 0 (因为 arrPtr 本身就是一个指向数组的指针)
     * @return 构造出的常量
     */
    private Constant makeArrayConstAndInsertStoreForNonConstImpl(Value arrPtr, InitValue initValue, List<Integer> indices) {
        if (initValue instanceof InitExp) {
            final var initExp = (InitExp) initValue;
            final var value = initExp.exp;
            if (value instanceof Constant) {
                return (Constant) value;
            } else {
                // TODO: Constant cache
                final var ptr = builder.insertGEPByInts(arrPtr, indices);
                builder.insertStore(ptr, value);

                return Constant.getZeroByType(value.getType());
            }
        } else {
            final var initElms = ((InitArray) initValue).elms;
            final var elms = new ArrayList<Constant>();

            for (int i = 0; i < initElms.size(); i++) {
                indices.add(i);
                final var elm = makeArrayConstAndInsertStoreForNonConstImpl(arrPtr, initElms.get(i), indices);
                indices.remove(indices.size() - 1);

                elms.add(elm);
            }

            return Constant.createArrayConst(elms);
        }
    }

    private Constant getZeroElm(IRType baseType, List<Integer> shape) {
        assert shape.size() >= 1;

        if (shape.size() == 1) {
            return Constant.getZeroByType(baseType);
        } else {
            return Constant.createZeroArrayConst(baseType);
        }
    }

    private void newDefByFinalness(boolean isFinal, Variable var, Value val) {
        if (isFinal) {
            // Final variable 可以自然地跨 BasicBlock 使用
            // 因为其与 IR 的绑定关系不可能再变换
            // 永远也不需要 Phi
            final var finalInfo = builder.getFunction().getAnalysisInfo(FinalInfo.class);
            finalInfo.newDef(var, val);
        }

        final var versionInfo = builder.getBasicBlock().getAnalysisInfo(VersionInfo.class);
        versionInfo.newDef(var, val);
    }

    private Optional<Constant> findConstant(String name) {
        // TODO: 全局数组查找
        final var finalInfo = builder.getFunction().getAnalysisInfo(FinalInfo.class);
        return scope.get(name)
            .map(ScopeEntry::var)
            .flatMap(finalInfo::getNormalVar);
    }

    private Optional<AllocInst> findArray(String name) {
        final var finalInfo = builder.getFunction().getAnalysisInfo(FinalInfo.class);
        return scope.get(name)
            .map(ScopeEntry::var)
            .flatMap(finalInfo::getArrayVar);
    }

    private void ensureInitValIsExp(InitValContext ctx) {
        if (ctx.exp() == null) {
            throw new SemanticException(ctx, "Except a normal expression here");
        }
    }

    private void ensureInitValIsList(InitValContext ctx) {
        if (ctx.initVal() == null) {
            throw new SemanticException(ctx, "Except a list initializer here");
        }
    }
    //#endregion decl + initVal


//====================================================================================================================//


    //#region exp 表达式相关
    @Override
    public Value visitExp(ExpContext ctx) {
        return visitExpAdd(ctx.expAdd());
    }

    @Override
    public Value visitExpAdd(ExpAddContext ctx) {
        final var lhs = visitExpMul(ctx.expMul());

        if (ctx.expAdd() == null) {
            return lhs; // no RHS
        }

        final var rhs = visitExpAdd(ctx.expAdd());

        final var op = ctx.expAddOp().getText();
        return switch (op) {
            case "+" -> insertConvertForBinaryOp(lhs, rhs, builder::insertIAdd, builder::insertFAdd);
            case "-" -> insertConvertForBinaryOp(lhs, rhs, builder::insertISub, builder::insertFSub);
            default -> throw new SemanticException(ctx, "Unknown expAdd op: " + op);
        };
    }

    @Override
    public Value visitExpMul(ExpMulContext ctx) {
        final var lhs = visitExpUnary(ctx.expUnary());

        if (ctx.expMul() == null) {
            return lhs; // no RHS
        }

        final var rhs = visitExpMul(ctx.expMul());

        final var op = ctx.expMulOp().getText();
        return switch (op) {
            case "*" -> insertConvertForBinaryOp(lhs, rhs, builder::insertIMul, builder::insertFMul);
            case "/" -> insertConvertForBinaryOp(lhs, rhs, builder::insertIDiv, builder::insertFDiv);
            case "%" -> {
                final var commonType = findCommonType(lhs.getType(), rhs.getType());
                if (commonType.isFloat()) {
                    throw new SemanticException(ctx, "Cannot use % on float");
                } else {
                    yield builder.insertIMod(lhs, rhs);
                }
            }

            default -> throw new SemanticException(ctx, "Unknown expMul op: " + op);
        };
    }

    @Override
    public Value visitExpUnary(ExpUnaryContext ctx) {
        if (ctx.atom() != null) {
            return visitAtom(ctx.atom());
        } else if (ctx.expUnaryOp() != null) {
            final var arg = visitExpUnary(ctx.expUnary());
            final var op = ctx.expUnaryOp().getText();
            return switch (op) {
                case "+" -> arg;
                case "-" -> insertConvertForUnaryOp(arg, builder::insertINeg, builder::insertFNeg);
                case "!" -> throw new LogNotAsUnaryExpException(arg);
                default -> throw new SemanticException(ctx, "Unknown expUnary op: " + op);
            };
        } else {
            // function call
            final var funcName = ctx.Ident().getText();
            final var func = currModule.getFunctions().get(funcName);

            if (func == null) {
                throw new SemanticException(ctx, "Unknown func: " + funcName);
            }

            // TODO: 函数参数的语义检查
            return builder.insertCall(func, visitFuncArgList(ctx.funcArgList()));
        }
    }

    @Override
    public List<Value> visitFuncArgList(FuncArgListContext ctx) {
        return ctx.exp().stream().map(this::visitExp).collect(Collectors.toList());
    }

    @Override
    public Value visitAtom(AtomContext ctx) {
        if (ctx.exp() != null) {
            return visitExp(ctx.exp());
        } else if (ctx.atomLVal() != null) {
            return visitAtomLVal(ctx.atomLVal());
        } else if (ctx.IntConst() != null) {
            final var ic = parseInt(ctx.IntConst().getText());
            return Constant.createIntConstant(ic);
        } else {
            final var fc = parseFloat(ctx.FloatConst().getText());
            return Constant.createFloatConstant(fc);
        }
    }

    @Override
    public Value visitAtomLVal(AtomLValContext ctx) {
        final var lValResult = visitLVal(ctx.lVal());

        if (lValResult.isVar) {
            final var versionInfo = builder.getBasicBlock().getAnalysisInfo(VersionInfo.class);
            final var variable = lValResult.var;

            return versionInfo.getDef(variable)
                .orElseThrow(() -> new SemanticException(ctx, "Not a variable: " + variable.name));

        } else {
            return builder.insertLoad(lValResult.gep);
        }
    }

    private static int parseInt(String text) {
        if (text.length() >= 2 && text.charAt(1) == 'x' || text.charAt(1) == 'X') {
            return Integer.parseInt(text.substring(2), 16);
        } else if (text.charAt(0) == '0') {
            if (text.equals("0")) {
                return 0;
            } else {
                return Integer.parseInt(text.substring(1), 8);
            }
        } else {
            return Integer.parseInt(text, 10);
        }
    }

    private static float parseFloat(String text) {
        return Float.parseFloat(text);
    }

    static class LValResult {
        public LValResult(boolean isVar, Variable var, GEPInst gep) {
            this.isVar = isVar;
            this.var = var;
            this.gep = gep;
        }

        final boolean isVar;
        final Variable var;
        final GEPInst gep;
    }

    @Override
    public LValResult visitLVal(LValContext ctx) {
        final var name = ctx.Ident().getText();
        final var entry = scope.get(name)
            .orElseThrow(() -> new SemanticException(ctx, "Unknown identifier: " + name));
        final var variable = entry.var;

        final var indices = ctx.exp().stream().map(this::visitExp).collect(Collectors.toList());
        if (indices.isEmpty()) {
            return new LValResult(true, variable, null);

        } else {
            // 因为数组本身就带一个指针
            final var prefixedIndices = new ArrayList<Value>();
            prefixedIndices.add(Constant.INT_0);
            prefixedIndices.addAll(indices);

            final var arrPtr = findArray(name)
                .orElseThrow(() -> new SemanticException(ctx, "Not a function: " + name));
            final var gep = builder.insertGEP(arrPtr, prefixedIndices);

            return new LValResult(false, null, gep);
        }
    }

    private static class LogNotAsUnaryExpException extends RuntimeException {
        LogNotAsUnaryExpException(Value arg) {
            super("LogNot exist in UnaryExp");
            this.arg = arg;
        }

        Value arg;
    }

    private Value insertConvertForBinaryOp(
        Value lhs,
        Value rhs,
        BiFunction<Value, Value, Value> intMerger,
        BiFunction<Value, Value, Value> floatMerger
    ) {
        final var commonType = findCommonType(lhs.getType(), rhs.getType());
        final var newLHS = insertConvertByType(commonType, lhs);
        final var newRHS = insertConvertByType(commonType, rhs);

        if (commonType.isInt()) {
            return intMerger.apply(newLHS, newRHS);
        } else {
            return floatMerger.apply(newLHS, newRHS);
        }
    }

    private Value insertConvertForUnaryOp(
        Value arg,
        java.util.function.Function<Value, Value> intMerger,
        java.util.function.Function<Value, Value> floatMerger
    ) {
        if (arg.getType().isInt()) {
            return intMerger.apply(arg);
        } else {
            return floatMerger.apply(arg);
        }
    }

    private Value insertConvertByType(IRType targetType, Value value) {
        final var srcType = value.getType();
        if (targetType.equals(srcType)) {
            return value;
        } else {
            if (targetType.isFloat() && srcType.isInt()) {
                return builder.insertI2F(value);
            } else {
                return builder.insertF2I(value);
            }
        }
    }

    private static IRType findCommonType(IRType ty1, IRType ty2) {
        assert ty1.isInt() || ty1.isFloat();
        assert ty2.isInt() || ty2.isFloat();

        if (ty1.equals(ty2)) {
            // 两个都是 Int 或者是两个都 Float 的情况
            return ty1; // or return ty2
        } else {
            // 一个 Int, 一个 Float 的情况
            return IRType.FloatTy;
        }
    }
    //#endregion exp


//====================================================================================================================//


    @Override
    public Void visitStmt(StmtContext ctx) {
        if (ctx.block() != null) {
            visitBlock(ctx.block());
        } else if (ctx.stmtIf() != null) {
            visitStmtIf(ctx.stmtIf());
        } else if (ctx.stmtWhile() != null) {
            visitStmtWhile(ctx.stmtWhile());
        } else if (ctx.exp() != null) {
            visitExp(ctx.exp());
        } else if (ctx.lVal() != null) {
            // 赋值语句
            final var lValResult = visitLVal(ctx.lVal());
            final var value = visitExp(ctx.exp());

            if (lValResult.isVar) {
                final var versionInfo = builder.getBasicBlock().getAnalysisInfo(VersionInfo.class);
                final var variable = lValResult.var;

                versionInfo.killOrNewDef(variable, value);
            } else {
                builder.insertStore(lValResult.gep, value);
            }
        } else if (ctx.Break() != null) {
            final var target = currWhileExit
                .orElseThrow(() -> new SemanticException(ctx, "Break out of while"));
            builder.insertBranch(target);
        } else if (ctx.Continue() != null) {
            final var target = currWhileCond
                .orElseThrow(() -> new SemanticException(ctx, "Continue out of while"));
            builder.insertBranch(target);
        } else if (ctx.Return() != null) {
            if (ctx.exp() != null) {
                final var val = visitExp(ctx.exp());
                builder.insertReturn(val);
            } else {
                builder.insertReturn();
            }
        } else {/* 空语句, 啥也不干 */}

        return null;
    }

    @Override
    public Void visitBlock(BlockContext ctx) {
        ctx.children.forEach(this::visit);
        return null;
    }

    @Override
    public Object visitStmtIf(StmtIfContext ctx) {
        // TODO Auto-generated method stub
        return super.visitStmtIf(ctx);
    }

    @Override
    public Object visitStmtWhile(StmtWhileContext ctx) {
        // TODO Auto-generated method stub
        return super.visitStmtWhile(ctx);
    }

    //#region 辅助函数
    private static IRType toIRType(String bType) {
        return switch (bType) {
            case "int" -> IRType.IntTy;
            case "float" -> IRType.FloatTy;
            case "void" -> IRType.VoidTy;
            default -> throw new RuntimeException("Unknown BType: " + bType);
        };
    }

    /**
     * 根据数组的 "形状 (Shape)" 来递归生成数组类型 <p>
     *
     * 例子:
     * <ul>
     *  <li> baseTy: int,      shape: []         ==> IntTy                      </li>
     *  <li> baseTy: int,      shape: [2, 3]     ==> [2 x [3 x IntTy]]          </li>
     *  <li> baseTy: float     shape: [-1, 3, 7] ==> *[3 x [7 x FloatTy]]       </li>
     * </ul>
     *
     * 上面三个例子分别对应 SysY 里对应的声明:
     * <ul>
     *  <li> int a                              </li>
     *  <li> int a[2][3]                        </li>
     *  <li> int a[][3][7]     (函数参数类型)   </li>
     * </ul>
     *
     * @param baseTy 元素类型
     * @param shape 数组形状
     * @return 构建出的数组/变量类型
     */
    public static IRType createTypeByShape(IRType baseTy, List<Integer> shape) {
        if (shape.isEmpty()) {
            return baseTy;
        } else {
            final var firstDim = shape.get(0);
            final var restShape = shape.subList(1, shape.size());
            final var restType = createTypeByShape(baseTy, restShape);

            if (firstDim < 0) {
                return IRType.createPtrTy(restType);
            } else {
                return IRType.createArrayTy(firstDim, restType);
            }
        }
    }

    /**
     * 根据函数形参的形状生成对应的类型 <p>
     *
     * 对变量而言, 其类型就是变量; 对数组而言, 其类型就是指向数组的指针
     *
     * @param baseTy 基本类型
     * @param shape 数组形状
     * @return 生成后的适合作为函数形参的类型
     */
    public static IRType createTypeForArgumentByShape(IRType baseTy, List<Integer> shape) {
        final var type = createTypeByShape(baseTy, shape);
        if (type instanceof SimpleIRTy) {
            // 普通类型的值直接按值传递
            return type;
        } else {
            // 数组类型的参数就传递数组的指针

            // 第零维为空的数组会被 createTypeByShape 翻译成指针
            // 所以不管 type 是指针还是数组, 都得再套一层指针
            return IRType.createPtrTy(type);
        }
    }

    static class ScopeEntry {
        public ScopeEntry(Variable var, IRType type) {
            this.var = var;
            this.type = type;
        }

        public Variable var() { return var; }

        final Variable var;
        final IRType type;
    }

    private Module currModule;
    private IRBuilder builder; // 非常非常偶尔的情况下它是 null, 并且在用的时候它必然是有的
    private ChainMap<ScopeEntry> scope; // identifier --> variable

    private Optional<BasicBlock> currWhileCond;
    private Optional<BasicBlock> currWhileExit;

    // flags
    private boolean inGlobal() {
        return builder == null || builder.getFunction() == null;
    }
    //#endregion
}
