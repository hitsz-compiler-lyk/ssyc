package utils;

import frontend.SourceCodeSymbol;
import frontend.info.CurrDefInfo;
import ir.*;
import ir.Module;
import ir.constant.ArrayConst;
import ir.constant.ArrayConst.ZeroArrayConst;
import ir.constant.Constant;
import ir.constant.FloatConst;
import ir.constant.IntConst;
import ir.inst.*;
import ir.type.ArrayIRTy;
import ir.type.IRTyKind;
import ir.type.IRType;
import ir.type.PointerIRTy;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

public class LLVMDumper {
    public LLVMDumper(OutputStream outStream) {
        this.writer = new PrintWriter(outStream);
        this.nameMap = new ChainMap<>();
    }

    public void close() {
        this.writer.close();
    }

    public void dump(Module module) {
        ir("target datalayout = \"e-m:e-p:32:32-Fi8-i64:64-v128:64:128-a:0:32-n32-S64\"");
        ir("target triple = \"armv7-unknown-linux-gnueabihf\"");
        newline();

        for (final var arrayConst : module.getArrayConstants()) {
            recordGlobal(arrayConst);
            dumpGlobalConstant(arrayConst);
        }

        for (final var global : module.getVariables()) {
            recordGlobal(global);
            dumpGlobalVariable(global);
        }

        // 先输出外部函数
        for (final var func : module.getFunctions()) {
            nameMap.put(func, func.getSymbol().getLLVMExternal());
            if (func.isExternal()) {
                dumpExternalFunction(func);
            }
        }

        ir("declare void @llvm.memcpy.p0i8.p0i8.i32(i8* noalias nocapture writeonly, i8* noalias nocapture readonly, i32, i1 immarg)");

        newline(); newline(); // two empty line

        // 再输出正常的函数
        for (final var func : module.getFunctions()) {
            nameMap.put(func, func.getSymbol().getLLVMExternal());
            if (!func.isExternal()) {
                dumpNormalFunction(func);
            }
        }

    }

    private void dumpNormalFunction(Function function) {
        inFunction();

        for (final var param : function.getParameters()) {
            recordLocal(param);
        }

        for (final var block : function) {
            recordLocal(block);
            block.stream().filter(i -> !i.getType().isVoid()).forEach(this::recordLocal);
        }

        ir("define dso_local <return-ty> <func-name>(<param*>) {",
                function.getType().getReturnType(), function.getSymbol().getLLVMExternal(), joinWithRef(function.getParameters()));

        for (final var param : function.getParameters()) {
            ir("; <param-name> : <param-ir-name>", param.getParamName(), param);
        }

        for (final var block : function.asElementView()) {
            final var preds = commaSplitList(block.getPredecessors(), b -> b.getSymbol().getVSCodeDescriptor());
            ir("<label>:    ; <label-name> : <pred*>", toName(block).substring(1), block.getSymbol().getVSCodeDescriptor(), preds);

            indent += 2;
            block.asElementView().forEach(this::dumpInstruction);
            indent -= 2;

            if (block.containsAnalysisInfo(CurrDefInfo.class)) {
                for (final var entry : block.getAnalysisInfo(CurrDefInfo.class).getAllEntries()) {
                    final var name = entry.getKey().getLLVMLocal();
                    final var def = entry.getValue().getCurrDef();
                    ir("  ; <name> -> <def>", name, def);
                }
            }
            newline();
        }

        ir("}");
        newline();

        outFunction();
    }

    private void newline() {
        ir("");
    }

    private void dumpExternalFunction(Function function) {
        final var returnType = function.getType().getReturnType();
        final var paramTypes = commaSplitList(function.getType().getParamTypes(), this::dumpIRType);

        ir("declare dso_local <return-ty> <func-name>(<parma-type*>)",
                returnType, function.getSymbol().getLLVMExternal(), paramTypes);
    }

    private void dumpGlobalVariable(GlobalVar gv) {
        final var name = toName(gv);
        final var init = gv.getInit();
        final var baseType = gv.getType().getBaseType();

        if (baseType instanceof PointerIRTy) { // array
            ir("<name> = dso_local global <arr-ptr-type> getelementptr inbounds (<base-type>, <init-ty>* <init-name>, i32 0, i32 0), align 4",
                    name, baseType, init.getType(), init.getType(), toName(init));

        } else { // variable
            ir("<name> = dso_local global <init>, align 4", name, init);
        }
        ir("; global: <symbol>", gv.getSymbol());
    }

    private void dumpInstruction(Instruction inst) {
        if (!(inst instanceof MemInitInst || inst instanceof CAllocInst)) {
            pir(" ".repeat(indent));

            // 因为 CAlloc 与 LLVM IR 中的 Alloc 的不同之处, CAlloc 语句虽然不是 VoidType, 也不能直接写成 %n = xxx 的形式
            if (!inst.getType().isVoid()) {
                pir("<inst-name> = ", toName(inst)); // "%1 = "
            }
        }

        if (inst instanceof BinaryOpInst) {
            final var bop = (BinaryOpInst) inst;
            pir("<binop> <ty> <lhs-name>, <rhs-name>",
                    getBinOpName(bop), bop.getType(), toName(bop.getLHS()), toName(bop.getRHS()));

        } else if (inst instanceof UnaryOpInst) {
            final var uop = (UnaryOpInst) inst;
            final var kind = uop.getKind();

            if (kind.isInt()) {
                pir("sub i32 0, <op-name>", toName(uop.getArg()) );
            } else if (kind.isFloat()) {
                pir("fneg <val>", uop.getArg());
            } else {
                throw new RuntimeException("Unknown UnaryOp kind: " + kind);
            }

        } else if (inst instanceof IntToFloatInst) {
            pir("sitofp <from> to float", ((IntToFloatInst) inst).getFrom());

        } else if (inst instanceof FloatToIntInst) {
            pir("fptosi <from> to i32", ((FloatToIntInst) inst).getFrom());

        } else if (inst instanceof BoolToIntInst) {
            pir("zext <from> to i32", ((BoolToIntInst) inst).getFrom());

        } else if (inst instanceof CmpInst) {
            final var cmp = (CmpInst) inst;
            pir("<cmpop> <ty> <lhs-name>, <rhs-name>",
                    getCmpOpName(cmp), cmp.getLHS().getType(), toName(cmp.getLHS()), toName(cmp.getRHS()));

        } else if (inst instanceof BrInst) {
            pir("br <nextBB>", ((BrInst) inst).getNextBB());

        } else if (inst instanceof BrCondInst) {
            final var br = (BrCondInst) inst;
            pir("br <cond>, <trueBB>, <falseBB>", br.getCond(), br.getTrueBB(), br.getFalseBB());

        } else if (inst instanceof PhiInst) {
            final var phi = (PhiInst) inst;

            if (phi.getIncomingSize() == 0) {
                final var type = phi.getType();
                if (type.isInt()) {
                    pir("add i32 0, 0 ; non-incoming phi for <symbol>", phi.getWaitFor());
                } else if (type.isFloat()) {
                    pir("fadd float 0, 0 ; non-incoming phi for <symbol>", phi.getWaitFor());
                } else {
                    throw new RuntimeException("Unknown type of empty phi: " + type);
                }
                newline();
                return;
            }

            final var incomingStrings = new ArrayList<String>();
            for (final var info : phi.getIncomingInfos()) {
                final var str = "[ %s, %s ]".formatted(toName(info.getValue()), toName(info.getBlock()));
                incomingStrings.add(str);
            }

            pir("phi <incoming-type> <incoming*>",
                    phi.getType(),
                    String.join(", ", incomingStrings));

        } else if (inst instanceof ReturnInst) {
            final var returnVal = ((ReturnInst) inst).getReturnValue();

            if (returnVal.isPresent()) {
                pir("ret <val>", returnVal.get());
            } else {
                pir("ret void");
            }

        } else if (inst instanceof CallInst) {
            final var call = (CallInst) inst;
            pir("call <ret-ty> <func-name>(<arg*>)",
                inst.getType(),
                toName(call.getCallee()),
                joinWithRef(call.getArgList()));

        } else if (inst instanceof GEPInst) {
            final var gep = (GEPInst) inst;
            pir("getelementptr <base-type>, <ptr>, <index*>",
                    ((PointerIRTy) gep.getPtr().getType()).getBaseType(),
                    gep.getPtr(),
                    joinWithRef(gep.getIndices()));

        } else if (inst instanceof LoadInst) {
            final var load = (LoadInst) inst;
            pir("load <ty>, <ptr>", load.getType(), load.getPtr());

        } else if (inst instanceof StoreInst) {
            final var store = (StoreInst) inst;
            pir("store <val>, <ptr>", store.getVal(), store.getPtr());

        } else if (inst instanceof CAllocInst) {
            final var calloc = (CAllocInst) inst;
            final var allocaTemp = tempLocal();

            ir("<alloca-temp> = alloca <base-type>, align 8", allocaTemp, calloc.getAllocType());
            ir("<calloc-name> = getelementptr <base-type>, <calloc-type>* <alloca-temp>, i32 0, i32 0",
                toName(calloc), calloc.getAllocType(), calloc.getAllocType(), allocaTemp);
            ir("; <symbol>", calloc.getSymbol());

        } else if (inst instanceof MemInitInst) {
            final var meminit = (MemInitInst) inst;
            final var init = meminit.getInit();
            final var arrPtr = meminit.getArrayPtr();

            final var size = init.getType().getSize();
            final var srcName = tempLocal();
            final var dstName = tempLocal();

            ir("<name> = bitcast <src> to i8*", srcName, arrPtr);
            ir("<name> = bitcast <init-type>* <init-name> to i8*", dstName, init.getType(), toName(init));

            final var fmt = "call void @llvm.memcpy.p0i8.p0i8.i32("
                          + "i8* align 4 <src-name>, "
                          + "i8* align 4 <dst-name>, "
                          + "i32 <size>, i1 false)";
            ir(fmt, srcName, dstName, size);
            ir("; <symbol>", meminit.getArrayPtr().getSymbol());

        } else {
            throw new RuntimeException("Unknown instruction type: " + inst.getKind());
        }

        if (inst.getSymbolOpt().isPresent()) {
            pir("    ; <symbol>", inst.getSymbol());
        }

        newline();
    }

    private void dumpGlobalConstant(Constant constant) {
        if (constant instanceof ArrayConst) {
            ir("<name> = dso_local global <constant>, align 4", toName(constant), dumpConstant(constant));
            ir("; init: <symbol>", constant.getSymbolOpt().map(SourceCodeSymbol::toString).orElse(""));
        } else {
            throw new RuntimeException("Global constant should only be array");
        }
    }

    private String dumpConstant(Constant constant) {
        if (constant instanceof IntConst) {
            return "i32 " + constant;

        } else if (constant instanceof FloatConst) {
            return "float " + constant;

        } else if (constant instanceof ArrayConst) {
            final var type = dumpIRType(constant.getType());

            if (constant instanceof ZeroArrayConst) {
                return "%s zeroinitializer".formatted(type);
            } else {
                final var elms = commaSplitList(((ArrayConst) constant).getRawElements(), this::dumpConstant);
                return "%s [%s]".formatted(type, elms);
            }

        } else {
            throw new RuntimeException("Unknown constant type: " + constant);
        }
    }


    /**
     * 将 type 输出为 llvm ir 中的类型
     * @param type value 类型
     * @return ir string
     */
    private String dumpIRType(IRType type) {
        final var kind = type.getKind();
        return switch (kind) {
            case Int -> "i32";
            case Bool -> "i1";
            case Float -> "float";
            case Void -> "void";
            case Pointer -> dumpIRType(((PointerIRTy) type).getBaseType()) + "*";
            case Array -> {
                final var array = (ArrayIRTy) type;
                yield "[%d x %s]".formatted(array.getElementNum(), dumpIRType(array.getElementType()));
            }

            case BBlock -> "label";
            case Parameter -> dumpIRType(((Parameter) type).getParamType());

            case Function -> throw new RuntimeException("Function type needn't be used.");
            default -> throw new RuntimeException("Unknown IRType kind: " + kind);
        };
    }

    private String getBinOpName(BinaryOpInst bop) {
        return switch (bop.getKind()) {
            case IAdd -> "add";
            case ISub -> "sub";
            case IMul -> "mul";
            case IDiv -> "sdiv";
            case IMod -> "srem";
            case FAdd -> "fadd";
            case FSub -> "fsub";
            case FMul -> "fmul";
            case FDiv -> "fdiv";
            default -> throw new RuntimeException("Unknown bop kind: " + bop.getKind());
        };
    }

    private String getCmpOpName(CmpInst cmp) {
        return switch (cmp.getKind()) {
            // For icmp, see: https://llvm.org/docs/LangRef.html#icmp-instruction
            case ICmpEq -> "icmp eq";
            case ICmpNe -> "icmp ne";
            case ICmpGt -> "icmp sgt";
            case ICmpGe -> "icmp sge";
            case ICmpLt -> "icmp slt";
            case ICmpLe -> "icmp sle";
            // For fcmp, see: https://llvm.org/docs/LangRef.html#fcmp-instruction
            case FCmpEq -> "fcmp ueq";
            case FCmpNe -> "fcmp une";
            case FCmpGt -> "fcmp ugt";
            case FCmpGe -> "fcmp uge";
            case FCmpLt -> "fcmp ult";
            case FCmpLe -> "fcmp ule";
            default -> throw new RuntimeException("Unknown cmp kind: " + cmp.getKind());
        };
    }

    /**
     * 输出 Value 在 IR 中常见的引用形式
     * @param value Value 类型
     * @return "%s %s".format(type, name)
     */
    private String getReference(Value value) {
        if (value.getType().getKind() == IRTyKind.Void) {
            return "void";
        } else {
            return String.format("%s %s", dumpIRType(value.getType()), toName(value));
        }
    }

    /**
     * <p>
     * 用于便捷表示输出 LLVM IR 的方法.
     * </p>
     *
     * <p>
     * 格式串中所有的 {@code <.*?>} 都等价于 %s, 而对于可变参数中的对象而言,
     * Value 类型的对象会输出 getReference(o), IRType 类型的对象会输出 dumpIRType(o),
     * 其他的对象的输出等价于 o.toString()
     * </p>
     * @param fmt 格式串
     * @param args 可变参数
     */
    private void ir(String fmt, Object... args) {
        writer.print(" ".repeat(indent));
        pir(fmt, args);
        writer.println();
    }

    private void pir(String fmt, Object... args) {
        final var newArgs = Arrays.stream(args).map(o -> {
            if (o instanceof Value) {
                return getReference((Value) o);
            } else if (o instanceof IRType) {
                return dumpIRType((IRType) o);
            } else {
                return o;
            }
        }).toArray();

        writer.print(fmt.replaceAll("<.*?>", "%s").formatted(newArgs));
    }

    private <T> String commaSplitList(List<? extends T> list, java.util.function.Function<T, String> transformer) {
        return list.stream().map(transformer).collect(Collectors.joining(", "));
    }

    private String joinWithRef(List<? extends Value> list) {
        return commaSplitList(list, this::getReference);
    }

    private void recordLocal(Value value) {
        final var name = "%_" + localCount++;
        nameMap.put(value, name);
    }

    private String tempLocal() {
        return "%_" + localCount++;
    }

    private void recordGlobal(Value value) {
        final var name = "@_" + globalCount++;
        nameMap.put(value, name);
    }

    private String toName(Value value) {
        if (value instanceof IntConst || value instanceof FloatConst) {
            return value.toString();
        } else {
            return nameMap.get(value).orElseThrow(() -> new RuntimeException("Value not found: " + value));
        }
    }

    private void inFunction() {
        nameMap = new ChainMap<>(nameMap);
        localCount = 0;
    }

    private void outFunction() {
        nameMap = nameMap.getParent().orElseThrow();
    }

    private int globalCount = 0;
    private int localCount = 0;
    private ChainMap<Value, String> nameMap;
    private final PrintWriter writer;
    private int indent;
}
