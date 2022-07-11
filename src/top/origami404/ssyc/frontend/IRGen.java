package top.origami404.ssyc.frontend;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.ParserRuleContext;
import top.origami404.ssyc.frontend.SemanticException.GenExpInGlobalException;
import top.origami404.ssyc.frontend.SysYParser.*;
import top.origami404.ssyc.frontend.info.FinalInfo;
import top.origami404.ssyc.frontend.info.VersionInfo;
import top.origami404.ssyc.frontend.info.VersionInfo.Variable;
import top.origami404.ssyc.ir.*;
import top.origami404.ssyc.ir.Module;
import top.origami404.ssyc.ir.constant.ArrayConst;
import top.origami404.ssyc.ir.constant.Constant;
import top.origami404.ssyc.ir.constant.IntConst;
import top.origami404.ssyc.ir.inst.AllocInst;
import top.origami404.ssyc.ir.inst.GEPInst;
import top.origami404.ssyc.ir.inst.PhiInst;
import top.origami404.ssyc.ir.type.IRType;
import top.origami404.ssyc.ir.type.SimpleIRTy;
import top.origami404.ssyc.utils.ChainMap;
import top.origami404.ssyc.utils.Log;

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

        visitBlock(ctx.block());

        // 翻译完所有基本块之后再填充翻译过程中遗留下来的空白 phi
        // 这样可以避免翻译的时候的未封闭的(unsealed)基本块带来的麻烦
        function.getBasicBlocks().forEach(this::fillIncompletedPhiForBlock);

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
        /*
            这里的 def 实际上有 6 种情况:

                      全局        局部
            常量    全局常量    局部常量
            变量    全局变量    局部变量
            数组    全局数组    局部数组

            它们总是需要将 identifier 与 variable 的对应关系加入到作用域中,
            也总是需要更新 variable 与其当前的定义的关系
            (不过是要在不同的表中, 有的情况是 finalInfo, 有的情况是 versionInfo)

            全局常量: 更新 scope, 加入 finalInfo, 加入 globalConst
            局部常量: 更新 scope, 加入 finalInfo
            全局变量: 更新 scope, (以指针形式) 加入 finalInfo, 加入 globalVariable (绑定0/初始值)
            局部变量: 更新 scope, 加入 versionInfo (绑定0/初始值)
            全局数组: 更新 scope, 加入 finalInfo, 加入 globalVariable (绑定0/初始值)
                有非空初始值的情况下, 初始值加入 globalConst
            局部数组: 更新 scope, 加入 finalInfo
                有非空初始值的情况下, 插入 MemInit, 初始值加入 globalConst
         */


        final var info = visitLValDecl(ctx.lValDecl());
        final var name = info.name;
        final var shape = info.shape;
        final var type = createTypeByShape(baseType, shape);

        // 大家都要加入 scope
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

        final var finalInfo = builder.getBasicBlock().getAnalysisInfo(FinalInfo.class);
        final var versionInfo = builder.getBasicBlock().getAnalysisInfo(VersionInfo.class);

        if (shape.isEmpty()) {
            // 对简单的变量, 只需要在有初始值的时候求值, 无初始值时默认零初始化即可
            final var value = Optional.ofNullable(ctx.initVal())
                .flatMap(n -> Optional.ofNullable(n.exp()))
                // 如果其初始化器需要运行时求值, 那么 visitExp 会将对应的求值指令插入到当前块中
                .map(this::visitExp)
                .orElse(Constant.getZeroByType(type));

            if (isConst) {
                // 对常量
                if (!(value instanceof Constant)) {
                    throw new SemanticException(ctx, "Only constant can be used to initialize a constant variable");
                }

                // 全局常量需要特别放到全局常量表中
                final var constant = (Constant) value;
                if (inGlobal()) {
                    currModule.getConstants().put(name, constant);
                }

                // 局部跟全局常量都丢到 finalInfo 中
                finalInfo.newDef(variable, value);

            } else {
                if (inGlobal()) {
                    // 全局变量
                    if (!(value instanceof Constant)) {
                        throw new SemanticException(ctx, "Only constant can be used to initialize a global variable");
                    }

                    // 全局变量需要做一个指针, 存到 GlobalVariable 中去
                    final var global = GlobalVar.createGlobalVariable(type, name, (Constant) value);
                    currModule.getVariables().put(name, global);
                    // 因为这个变量与该指针的关系是不变的, 所以丢 finalInfo
                    finalInfo.newDef(variable, global);

                } else {
                    // 普通变量只需要加入 versionInfo 中就可以了
                    versionInfo.newDef(variable, value);
                }
            }

        } else {
            if (inGlobal()) {
                // 对全局数组, 需要加入到 globalVariable 表中, 并且绑定初始值
                try {
                    final var initValue = visitInitVal(ctx.initVal(), isConst, baseType, shape);
                    final var constant = justMakeArrayConst(initValue);

                    if (constant instanceof ArrayConst) {
                        final var arrayConst = (ArrayConst) constant;
                        final var global = GlobalVar.createGlobalArray(type, name, arrayConst);

                        currModule.getVariables().put(name, global);
                        finalInfo.newDef(variable, global);
                    } else {
                        throw new RuntimeException();
                    }

                } catch (RuntimeException e) {
                    throw new SemanticException(ctx, "Only constant array can be used to initialize a global array");
                }

            } else {
                // 对局部数组, 需要特殊处理其初始值

                // 分配数组空间, 并且定义数组;
                // 数组变量的定义永远是指向对应空间的指针 (即那个 AllocInst), 并且永远不应该被重定义
                final var arrPtr = new AllocInst(type);
                finalInfo.newDef(variable, arrPtr);

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
                    "GlobalVar value can only be initiated by constant variable, not even constant array");
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

        Log.ensure(elms.size() == currDim);

        // 稍作处理 & 包装, 返回上层需要的信息
        final var used = curr - originalCurr;
        final var value = new InitArray(elms);
        return new MakeInitValueResult(used, value);
    }

    private Constant justMakeArrayConst(InitValue initValue) {
        if (initValue instanceof InitExp) {
            final var exp = ((InitExp) initValue).exp;
            if (exp instanceof Constant) {
                return (Constant) exp;
            } else {
                throw new RuntimeException();
            }
        } else {
            final var elms = ((InitArray) initValue).elms;
            final var vals = elms.stream().map(this::justMakeArrayConst).collect(Collectors.toList());
            return Constant.createArrayConst(vals);
        }
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
                // 由于常数折叠, 如果这个表达式真的能变成常量
                // 那 value 必然是一个常量
                // 虽然反之并不成立就是了... (所以对于非常量表达式这里也有可能变成常量的)
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
        Log.ensure(shape.size() >= 1);

        if (shape.size() == 1) {
            return Constant.getZeroByType(baseType);
        } else {
            return Constant.createZeroArrayConst(baseType);
        }
    }

    private Optional<Value> findArray(String name) {
        // 数组必然是 final, 因为数组被翻译成 IR 中数组的指针
        // 而数组指针指向谁永远不会变
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
        if (builder == null)  {
            throw new GenExpInGlobalException(ctx);
        }

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
            case "+" -> insertConvertForBinary(lhs, rhs, builder::insertIAdd, builder::insertFAdd);
            case "-" -> insertConvertForBinary(lhs, rhs, builder::insertISub, builder::insertFSub);
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
            case "*" -> insertConvertForBinary(lhs, rhs, builder::insertIMul, builder::insertFMul);
            case "/" -> insertConvertForBinary(lhs, rhs, builder::insertIDiv, builder::insertFDiv);
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
            final var finalInfo = builder.getBasicBlock().getAnalysisInfo(FinalInfo.class);

            final var variable = lValResult.var;
            final var type = scope.get(variable.name)
                .orElseThrow(() -> new SemanticException(ctx, "Unknown identifier: " + variable.name))
                .type;

            // 如果在全局的 finalInfo 与当前块的 versionInfo 里都找不到这个变量的话
            // 就先插入一个空白的 phi 当作定义
            return finalInfo.getDef(variable)
                .or(() -> versionInfo.getDef(variable))
                .orElseGet(() -> builder.insertEmptyPhi(type, variable));

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

    private Value insertConvertForBinary(
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
        Log.ensure(ty1.isInt() || ty1.isFloat());
        Log.ensure(ty2.isInt() || ty2.isFloat());

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


    //#region stmt 语句相关
    @Override
    public Void visitStmt(StmtContext ctx) {
        if (ctx.block() != null) {
            visitBlock(ctx.block());
        } else if (ctx.stmtIf() != null) {
            visitStmtIf(ctx.stmtIf());
        } else if (ctx.stmtWhile() != null) {
            visitStmtWhile(ctx.stmtWhile());
        } else if (ctx.stmtPutf() != null) {
            // TODO: 似乎不需要支持 putf ?
            throw new SemanticException(ctx, "Unsupported putf now");
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

    @Override public Object visitCond(CondContext ctx) { throw new UnsupportedOperationException("Shouldn't be called"); }
    @Override public Object visitLogOr(LogOrContext ctx) { throw new UnsupportedOperationException("Shouldn't be called"); }
    @Override public Object visitLogAnd(LogAndContext ctx) { throw new UnsupportedOperationException("Shouldn't be called"); }
    @Override public Object visitLogRel(LogRelContext ctx) { throw new UnsupportedOperationException("Shouldn't be called"); }

    public void visitCond(CondContext ctx, BasicBlock trueBB, BasicBlock falseBB) {
        visitLogOr(ctx.logOr(), trueBB, falseBB);
    }

    public void visitLogOr(LogOrContext ctx, BasicBlock trueBB, BasicBlock falseBB) {
        if (ctx.logOr() == null) {
            visitLogAnd(ctx.logAnd(), trueBB, falseBB);
            return;
        }

        final var nextCondBB = builder.createFreeBBlock(nameWithLineAndColumn(ctx, "or_rhs"));
        visitLogAnd(ctx.logAnd(), trueBB, nextCondBB);
        builder.appendBBlock(nextCondBB);
        visitLogOr(ctx.logOr(), trueBB, falseBB);
    }

    public void visitLogAnd(LogAndContext ctx, BasicBlock trueBB, BasicBlock falseBB) {
        if (ctx.logAnd() == null) {
            visitLogRel(ctx.logRel(), trueBB, falseBB);
        }

        final var nextCondBB = builder.createFreeBBlock(nameWithLineAndColumn(ctx, "and_rhs"));
        visitLogRel(ctx.logRel(), nextCondBB, falseBB);
        builder.appendBBlock(nextCondBB);
        visitLogAnd(ctx.logAnd(), trueBB, falseBB);
    }

    public void visitLogRel(LogRelContext ctx, BasicBlock trueBB, BasicBlock falseBB) {
        final var cond = visitRelEq(ctx.relEq());
        builder.insertBrCond(cond, trueBB, falseBB);
    }

    @Override
    public Value visitRelEq(RelEqContext ctx) {
        if (ctx.relEq() == null) {
            return visitRelComp(ctx.relComp());
        }

        final var lhs = visitRelComp(ctx.relComp());
        final var rhs = visitRelEq(ctx.relEq());
        final var op = ctx.relEqOp().getText();
        return switch (op) {
            case "==" -> insertConvertForBinary(lhs, rhs, builder::insertICmpEq, builder::insertFCmpEq);
            case "!=" -> insertConvertForBinary(lhs, rhs, builder::insertICmpNe, builder::insertFCmpNe);
            default -> throw new SemanticException(ctx, "Unknown relEq op: " + op);
        };
    }

    @Override
    public Value visitRelComp(RelCompContext ctx) {
        if (ctx.relComp() == null) {
            return visitRelExp(ctx.relExp());
        }

        final var lhs = visitRelExp(ctx.relExp());
        final var rhs = visitRelComp(ctx.relComp());
        final var op = ctx.relCompOp().getText();
        return switch (op) {
            case "<"  -> insertConvertForBinary(lhs, rhs, builder::insertICmpLt, builder::insertFCmpLt);
            case "<=" -> insertConvertForBinary(lhs, rhs, builder::insertICmpLe, builder::insertFCmpLe);
            case ">"  -> insertConvertForBinary(lhs, rhs, builder::insertICmpGt, builder::insertFCmpGt);
            case ">=" -> insertConvertForBinary(lhs, rhs, builder::insertICmpGe, builder::insertFCmpGe);
            default -> throw new SemanticException(ctx, "Unknown relComp op: " + op);
        };
    }

    @Override
    public Value visitRelExp(RelExpContext ctx) {
        try {
            final var val = visitExp(ctx.exp());
            Log.ensure(val.getType().isInt());
            return builder.insertICmpNe(val, IntConst.INT_0);
        } catch (LogNotAsUnaryExpException e) {
            final var val = e.arg;
            Log.ensure(val.getType().isInt());
            return builder.insertICmpEq(val, IntConst.INT_0);
        }
    }

    @Override
    public Void visitStmtIf(StmtIfContext ctx) {
        final var trueBB = builder.createFreeBBlock(nameWithLine(ctx, "if_then"));
        final var falseBB = builder.createFreeBBlock(nameWithLine(ctx, "if_else"));

        visitCond(ctx.cond(), trueBB, falseBB);

        builder.appendBBlock(trueBB);
        visitStmt(ctx.s1);

        builder.appendBBlock(falseBB);
        if (ctx.s2 != null) {
            visitStmt(ctx.s2);
        }

        return null;
    }

    @Override
    public Void visitStmtWhile(StmtWhileContext ctx) {
        final var bodyBB = builder.createFreeBBlock(nameWithLine(ctx, "while_body"));
        final var exitBB = builder.createFreeBBlock(nameWithLine(ctx, "while_exit"));

        visitCond(ctx.cond(), bodyBB, exitBB);

        currWhileCond = Optional.of(builder.getBasicBlock());
        currWhileExit = Optional.of(exitBB);

        builder.appendBBlock(bodyBB);
        visitStmt(ctx.stmt());

        currWhileCond = Optional.empty();
        currWhileExit = Optional.empty();

        builder.appendBBlock(exitBB);
        return null;
    }
    //#endregion stmt


//====================================================================================================================//


    //#region 填充空白 phi
    public void fillIncompletedPhiForBlock(BasicBlock bblock) {
        for (final var phi : bblock.phis()) {
            // 对于未完成的 Phi
            if (!phi.isIncompleted()) {
                continue;
            }

            fillIncompletedPhi(phi, bblock);
        }
    }

    public void fillIncompletedPhi(PhiInst phi, BasicBlock block) {
        final var type = phi.getType();
        final var variable = phi.getVariable();

        // 对每一个前继, 都要获得一个 value
        // 即对每个前继问: "当控制流从它这里来的时候这个 phi 要取什么 Value"
        final var incomingValues = block.getPredecessors().stream()
           .map(pred -> findDefinition(pred, variable, type))
           .collect(Collectors.toList());
        // 然后设置为这个 phi 的 incomingValue
        phi.setIncomingCO(incomingValues);

        // 随后尝试去掉这个 phi
        final var end = tryReplaceTrivialPhi(phi);
        if (end != phi) {
            // 如果能去掉
            // 首先删除原 phi 所有 incoming (会去除所有 user)
            phi.clearIncomingCO();
            // 然后将其从块中删除
            block.getIList().asElementView().remove(phi);
            // 然后将其所有出现都替换为 end
            phi.replaceAllUseWith(end);
        }
    }

    private Value tryReplaceTrivialPhi(PhiInst phi) {
        // incoming 先去重, 再删去自己
        final var incoming = new HashSet<>(phi.getIncomingValues());
        incoming.remove(phi);

        if (incoming.size() == 0) {
            throw new RuntimeException("Phi for undefined: " + phi.getVariable().getIRName());
        } else if (incoming.size() == 1) {
            // 如果去重后只有一个, 那么这个 Phi 是可以去掉的
            return incoming.iterator().next();
        } else {
            // 否则, 这个 phi 不可以去掉
            return phi;
        }
    }

    private Value findDefinition(BasicBlock bblock, Variable variable, IRType type) {
        final var versionInfo = bblock.getAnalysisInfo(VersionInfo.class);
        if (versionInfo.contains(variable)) {
            // 如果当前块有对它的定义, 那么就直接返回这个定义
            return versionInfo.getDef(variable).orElseThrow();
        }

        // 没有定义的话, 就要往上递归去找
        final var phi = new PhiInst(type, variable);
        // 为了防止递归无限循环, 得先插一个空 phi 在这里作为定义
        versionInfo.newDef(variable, phi);
        // 然后尝试去填充这个空 phi
        fillIncompletedPhi(phi, bblock);

        // 填充完之后看看这个 phi 是否可以被替代
        final var end = tryReplaceTrivialPhi(phi);
        if (end != phi) {
            // 可以的话就直接清空 phi 的 incoming
            phi.clearIncomingCO();
            // 然后将其定义替换为 end
            versionInfo.kill(variable, end);
        } else {
            // 不可替代的话就要往基本块里加入这个 phi
            bblock.addPhi(phi);
        }

        // 返回找到的定义
        return end;
    }
    //#endregion

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

    private String nameWithLineAndColumn(ParserRuleContext ctx, String prefix) {
        final var lineNo = ctx.getStart().getLine();
        final var column = ctx.getStart().getCharPositionInLine();
        return prefix + "_" + lineNo + "_" + column;
    }

    private static String nameWithLine(ParserRuleContext ctx, String prefix) {
        return prefix + "_" + ctx.getStart().getLine();
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
