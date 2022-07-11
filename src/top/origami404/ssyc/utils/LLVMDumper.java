package top.origami404.ssyc.utils;

import top.origami404.ssyc.ir.BasicBlock;
import top.origami404.ssyc.ir.Function;
import top.origami404.ssyc.ir.Parameter;
import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.constant.Constant;
import top.origami404.ssyc.ir.inst.*;
import top.origami404.ssyc.ir.type.ArrayIRTy;
import top.origami404.ssyc.ir.type.IRTyKind;
import top.origami404.ssyc.ir.type.IRType;
import top.origami404.ssyc.ir.type.PointerIRTy;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class LLVMDumper {
    public LLVMDumper(OutputStream outStream) {
        this.outStream = outStream;
        this.writer = new PrintWriter(outStream);
    }

    public void dump(Value value) {
        dumpAll(value);
    }

    private void dumpAll(Value value) {
        if (value instanceof Function) {
            writer.println(dumpFunction((Function) value));
        } else if (value instanceof Parameter) {
            writer.println(dumpParameter((Parameter) value));
        } else if (value instanceof BasicBlock ) {
            writer.println(dumpBasicBlock((BasicBlock) value));
        } else if (value instanceof Instruction ) {
            writer.println(dumpInstruction((Instruction) value));
        } else if (value instanceof Constant) {
            writer.println(dumpConstant((Constant) value));
        } else {
            throw new RuntimeException("Unknown IR type!");
        }
    }

    private String dumpFunction(Function function) {
        throw new UnsupportedOperationException("TODO");
        // TODO Function dump
    }

    private String dumpParameter(Parameter parameter) {
        throw new UnsupportedOperationException("TODO");
        // TODO Parameter dump
    }

    private String dumpBasicBlock(BasicBlock bblock) {
        throw new UnsupportedOperationException("TODO");
        // TODO BasicBlock dump
    }

    private String dumpInstruction(Instruction inst) {
        if (!inst.getType().isVoid()) {
            writer.print(inst.getName() + " = "); // "%1 = "
        }

        if (inst instanceof BinaryOpInst) {
            writer.println("%s %s %s, %s".formatted(
                    inst.getKind().isInt()
                            ? inst.getKind().toString().toLowerCase().substring(1)
                            : inst.getKind().toString().toLowerCase(),
                    dumpIRType(inst.getType()),
                    ((BinaryOpInst) inst).getLHS().getName(),
                    ((BinaryOpInst) inst).getLHS().getName()
                    ));

        } else if (inst instanceof UnaryOpInst) {
            if (inst.getKind().isInt()) {
                writer.println("sub i32 0, %s".formatted(
                        ((UnaryOpInst) inst).getArg().getName()
                ));
            } else if (inst.getKind().isFloat()) {
                writer.println("fneg %s".formatted(
                        getReference(((UnaryOpInst) inst).getArg())
                ));
            }

        } else if (inst instanceof IntToFloatInst) {
            writer.println("sitofp %s to float".formatted(getReference(((IntToFloatInst) inst).getFrom())));

        } else if (inst instanceof FloatToIntInst) {
            writer.println("fptosi %s to i32".formatted(getReference(((FloatToIntInst) inst).getFrom())));

        } else if (inst instanceof CmpInst) {
            writer.println("%s %s %s %s, %s".formatted(
                    inst.getKind().toString().substring(0, 4).toLowerCase(),
                    inst.getKind().toString().substring(4),
                    dumpIRType(inst.getType()),
                    ((CmpInst) inst).getLHS().getName(),
                    ((CmpInst) inst).getRHS().getName()
            ));

        } else if (inst instanceof BrInst) {
            writer.println("br %s".formatted(getReference(((BrInst) inst).getNextBB())));

        } else if (inst instanceof BrCondInst) {
            writer.println("br %s, %s, %s".formatted(
                    getReference(((BrCondInst) inst).getCond()),
                    getReference(((BrCondInst) inst).getTrueBB()),
                    getReference(((BrCondInst) inst).getFalseBB())
            ));

        } else if (inst instanceof PhiInst) {
            writer.println("phi %s %s".formatted(
                    dumpIRType(inst.getType()),
                    StreamSupport.stream(((PhiInst) inst).getIncomingInfos().spliterator(), false)
                            .map(info -> "[ %s, %s ]".formatted(info.getValue().getName(), info.getBlock().getName()))
                            .collect(Collectors.joining(", "))
            ));

        } else if (inst instanceof ReturnInst) {
            writer.println("ret %s".formatted(((ReturnInst) inst).getReturnValue()
                    .map(this::getReference).orElse("void")));

        } else if (inst instanceof CallInst) {
            writer.println("call %s %s(%s)".formatted(
                    dumpIRType(inst.getType()),
                    ((CallInst) inst).getCallee().getName(),
                    ((CallInst) inst).getArgList().stream().map(this::getReference)
                            .collect(Collectors.joining(", "))
            ));

        } else if (inst instanceof AllocInst) {
            writer.println("alloca %s, align 8".formatted(dumpIRType(inst.getType())));

        } else if (inst instanceof GEPInst) {
            writer.println("getelementptr %s, %s, %s".formatted(
                    dumpIRType(((PointerIRTy)((GEPInst) inst).getPtr().getType()).getBaseType()),
                    getReference(((GEPInst) inst).getPtr()),
                    ((GEPInst) inst).getIndices().stream().map(this::getReference)
                            .collect(Collectors.joining(", "))
            ));

        } else if (inst instanceof LoadInst) {
            writer.println("load %s, %s".formatted(
                    dumpIRType(inst.getType()),
                    getReference(((LoadInst) inst).getPtr())));
            
        } else if (inst instanceof StoreInst) {
            writer.println("store %s, %s".formatted(
                    getReference(((StoreInst) inst).getVal()),
                    getReference(((StoreInst) inst).getPtr())
            ));

        } else if (inst instanceof MemInitInst) {
            writer.println("call %s(%s, %s, %s, %s)".formatted(
                    "void @llvm.memcpy.p0i8.p0i8.i32",
                    "i8* align 4 bitcast (%s to i8*)".formatted(getReference(((MemInitInst) inst).getArrayPtr())),
                    "i8* align 4 bitcast (%s to i8*)".formatted(getReference(((MemInitInst) inst).getInit())),
                    "i32 %d".formatted(((PointerIRTy) ((MemInitInst) inst).getArrayPtr().getType()).getBaseType().getSize()),
                    "i1 false"
            ));

        }
        throw new RuntimeException("Unknown instruction type: "+inst.getKind());
    }

    private String dumpConstant(Constant constant) {
        throw new UnsupportedOperationException("TODO");
        // TODO Constant dump
    }


    /**
     * 将 type 输出为 llvm ir 中的类型
     * @param type value 类型
     * @return ir string
     */
    private String dumpIRType(IRType type) {
        return switch (type.getKind()) {
            case Int -> "i32";
            case Bool -> "i1";
            case Float -> "float";
            case Void -> throw new RuntimeException("Void can't be used.");
            case Array ->
                    String.format("[%d x %s]", ((ArrayIRTy) type).getElementNum(), dumpIRType(((ArrayIRTy) type).getElementType()));
            case Pointer -> dumpIRType(((PointerIRTy) type).getBaseType()) + "*";
            case Function -> throw new RuntimeException("Function type needn't be used.");
            case BBlock -> "label";
            case Parameter -> dumpIRType(((Parameter) type).getParamType());
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
            return String.format("%s %s", dumpIRType(value.getType()), value.getName());
        }
    }



    private OutputStream outStream;
    private PrintWriter writer;
}
