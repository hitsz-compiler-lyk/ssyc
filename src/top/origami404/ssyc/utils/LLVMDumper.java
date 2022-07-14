package top.origami404.ssyc.utils;

import top.origami404.ssyc.frontend.info.VersionInfo.Variable;
import top.origami404.ssyc.ir.*;
import top.origami404.ssyc.ir.Module;
import top.origami404.ssyc.ir.constant.ArrayConst;
import top.origami404.ssyc.ir.constant.Constant;
import top.origami404.ssyc.ir.constant.FloatConst;
import top.origami404.ssyc.ir.constant.IntConst;
import top.origami404.ssyc.ir.inst.*;
import top.origami404.ssyc.ir.type.ArrayIRTy;
import top.origami404.ssyc.ir.type.IRTyKind;
import top.origami404.ssyc.ir.type.IRType;
import top.origami404.ssyc.ir.type.PointerIRTy;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class LLVMDumper {
    public LLVMDumper(OutputStream outStream) {
        this.writer = new PrintWriter(outStream);
    }

    public void dump(Module module) {
        for (final var arrayConst : module.getArrayConstants().values()) {
            dumpGlobalConstant(arrayConst);
        }

        for (final var global : module.getVariables().values()) {
            dumpGlobalVariable(global);
        }

        for (final var func : module.getFunctions().values()) {
            dumpFunction(func);
        }
    }

    private void dumpFunction(Function function) {
        ir("define dso_local global <return-ty> <func-name>(<param*>) {",
                function.getType().getReturnType(), function.getName(), joinWithRef(function.getParameters()));

        for (final var block : function.asElementView()) {
            ir("<label>:", block.getLabelName());
            block.asElementView().forEach(this::dumpInstruction);
        }

        ir("}");
    }

    private void dumpGlobalVariable(GlobalVar gv) {
        final var name = gv.getName();
        final var init = gv.getInit();
        final var baseType = gv.getType().getBaseType();

        if (baseType instanceof PointerIRTy) { // array
            ir("<name>$addr = dso_local global <arr-ptr-type> getelementptr inbounds (<base-type>, <init>, i32 0, i32 0), align 4",
                    name, baseType, init.getType(), init);

        } else { // variable
            ir("<name> = dso_local global <init>, align 4", name, init);
        }
    }

    private void dumpInstruction(Instruction inst) {
        writer.print("  "); // indent

        // 因为 CAlloc 与 LLVM IR 中的 Alloc 的不同之处, CAlloc 语句虽然不是 VoidType, 也不能直接写成 %n = xxx 的形式
        if (!inst.getType().isVoid() && !(inst instanceof CAllocInst)) {
            writer.print(inst.getName() + " = "); // "%1 = "
        }

        if (inst instanceof BinaryOpInst) {
            final var bop = (BinaryOpInst) inst;
            ir("<binop> <ty> <lhs-name>, <rhs-name>",
                    getBinOpName(bop), bop.getType(), bop.getLHS().getName(), bop.getRHS().getName());

        } else if (inst instanceof UnaryOpInst) {
            final var uop = (UnaryOpInst) inst;
            final var kind = uop.getKind();

            if (kind.isInt()) {
                ir("sub i32 0, <op-name>", uop.getArg().getName());
            } else if (kind.isFloat()) {
                ir("fneg <val>", uop.getArg());
            } else {
                throw new RuntimeException("Unknown UnaryOp kind: " + kind);
            }

        } else if (inst instanceof IntToFloatInst) {
            ir("sitofp <from> to float", ((IntToFloatInst) inst).getFrom());

        } else if (inst instanceof FloatToIntInst) {
            ir("fptosi <from> to i32", ((FloatToIntInst) inst).getFrom());

        } else if (inst instanceof CmpInst) {
            final var cmp = (CmpInst) inst;
            ir("<cmpop> <ty> <lhs-name>, <rhs-name>",
                    getCmpOpName(cmp), cmp.getType(), cmp.getLHS().getName(), cmp.getRHS().getName());

        } else if (inst instanceof BrInst) {
            ir("br <nextBB>", ((BrInst) inst).getNextBB());

        } else if (inst instanceof BrCondInst) {
            final var br = (BrCondInst) inst;
            ir("br <cond> <trueBB> <falseBB>", br.getCond(), br.getTrueBB(), br.getFalseBB());

        } else if (inst instanceof PhiInst) {
            final var phi = (PhiInst) inst;
            final var incomingStrings = new ArrayList<String>();
            for (final var info : phi.getIncomingInfos()) {
                final var str = "[ %s, %s ]".formatted(info.getValue().getName(), info.getBlock().getName());
                incomingStrings.add(str);
            }

            ir("phi <incoming-type> <incoming*>",
                    phi.getType(),
                    String.join(", ", incomingStrings));

        } else if (inst instanceof ReturnInst) {
            ir("ret <val>", ((ReturnInst) inst).getReturnValue());

        } else if (inst instanceof CallInst) {
            final var call = (CallInst) inst;
            ir("call <ret-ty> <func-name>(<arg*>)",
                inst.getType(),
                call.getCallee().getName(),
                joinWithRef(call.getArgList()));

        } else if (inst instanceof CAllocInst) {
            final var calloc = (CAllocInst) inst;
            ir("<calloc-name>$alloca = alloca <base-type>, align 8", calloc.getName(), calloc.getAllocType());
            ir("<calloc-name> = getelementptr <base-type> <calloc-name>$alloca i32 0, i32 0",
                    calloc.getName(), calloc.getAllocType(), calloc.getName());

        } else if (inst instanceof GEPInst) {
            final var gep = (GEPInst) inst;
            ir("getelementptr <base-type> <ptr> <index*>",
                    ((PointerIRTy) gep.getPtr().getType()).getBaseType(),
                    gep.getPtr(),
                    joinWithRef(gep.getIndices()));

        } else if (inst instanceof LoadInst) {
            final var load = (LoadInst) inst;
            ir("load <ty> <ptr>", load.getType(), load.getPtr());
            
        } else if (inst instanceof StoreInst) {
            final var store = (StoreInst) inst;
            ir("store <val> <ptr>", store.getVal(), store.getPtr());

        } else if (inst instanceof MemInitInst) {
            final var meminit = (MemInitInst) inst;
            final var size = ((PointerIRTy) meminit.getArrayPtr().getType()).getBaseType().getSize();
            final var fmt = "call void @llvm.memcpy.p0i8.p0i8.i32("
                          + "i8* align 4 bitcast (<src> to i8*), "
                          + "i8* align 4 bitcast (<dst> to i8*), "
                          + "i32 <size>, i1 false)";
            ir(fmt, meminit.getArrayPtr(), meminit.getInit(), size);

        } else {
            throw new RuntimeException("Unknown instruction type: " + inst.getKind());
        }

    }

    private void dumpGlobalConstant(Constant constant) {
        if (constant instanceof ArrayConst) {
            ir("<name> = dso_local global <constant>, align 4", constant.getName(), dumpConstant(constant));
        } else {
            throw new RuntimeException("Global constant should only be array");
        }
    }

    private String dumpConstant(Constant constant) {
        if (constant instanceof IntConst) {
            return getReference(constant);

        } else if (constant instanceof FloatConst) {
            return getReference(constant);

        } else if (constant instanceof ArrayConst) {
            final var type = dumpIRType(constant.getType());
            final var elms = ((ArrayConst) constant).getRawElements().stream()
                .map(this::dumpConstant)
                .collect(Collectors.joining(", "));
            return "%s [%s]".formatted(type, elms);

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
            case Pointer -> dumpIRType(((PointerIRTy) type).getBaseType()) + "*";
            case Array -> {
                final var array = (ArrayIRTy) type;
                yield "[%d x %s]".formatted(array.getElementNum(), dumpIRType(array.getElementType()));
            }

            case BBlock -> "label";
            case Parameter -> dumpIRType(((Parameter) type).getParamType());

            case Void -> throw new RuntimeException("Void can't be used.");
            case Function -> throw new RuntimeException("Function type needn't be used.");
            default -> throw new RuntimeException("Unknown IRType kind: " + kind);
        };
    }

    private String getBinOpName(BinaryOpInst bop) {
        final var kindName = bop.getKind().toString().toLowerCase();
        return bop.getKind().isInt() ? kindName.substring(1) : kindName;
    }

    private String getCmpOpName(CmpInst cmp) {
        final var str = cmp.getKind().toString().toLowerCase();
        final var name = str.substring(0, 4);
        final var kind = str.substring(4);
        return name + " " + kind;
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
            return String.format("%s %s", dumpIRType(value.getType()), value.getName());
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
        final var newArgs = Arrays.stream(args).map(o -> {
            if (o instanceof Value) {
                return getReference((Value) o);
            } else if (o instanceof IRType) {
                return dumpIRType((IRType) o);
            } else {
                return o;
            }
        }).toArray();

        writer.println(fmt.replaceAll("<.*?>", "%s").formatted(newArgs));
    }

    private String joinWithRef(List<? extends Value> list) {
        return list.stream().map(this::getReference).collect(Collectors.joining(", "));
    }

    private final PrintWriter writer;
}
