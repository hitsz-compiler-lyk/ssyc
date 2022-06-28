package top.origami404.ssyc.utils;

import java.io.OutputStream;
import java.io.PrintWriter;

import top.origami404.ssyc.ir.Argument;
import top.origami404.ssyc.ir.BasicBlock;
import top.origami404.ssyc.ir.Function;
import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.constant.Constant;
import top.origami404.ssyc.ir.inst.Instruction;

public class LLVMDumper {
    public LLVMDumper(OutputStream outStream) {
        this.outStream = outStream;
        this.writer = new PrintWriter(outStream);
    }

    public void dump(Value value) {
        dumpAll(value);
    }

    private String dumpAll(Value value) {
        if (value instanceof Function func) {
            return dumpFunction(func);
        } else if (value instanceof Argument arg) {
            return dumpArgument(arg);
        } else if (value instanceof BasicBlock bb) {
            return dumpBasicBlock(bb);
        } else if (value instanceof Instruction inst) {
            return dumpInsruction(inst);
        } else if (value instanceof Constant c) {
            return dumpConstant(c);
        } else {
            throw new RuntimeException("Unknown IR type!");
        }
    }

    private String dumpFunction(Function function) {
        throw new UnsupportedOperationException("TODO");
    }

    private String dumpArgument(Argument argument) {
        throw new UnsupportedOperationException("TODO");
    }

    private String dumpBasicBlock(BasicBlock bblock) {
        throw new UnsupportedOperationException("TODO");
    }

    private String dumpInsruction(Instruction inst) {
        throw new UnsupportedOperationException("TODO");
    }

    private String dumpConstant(Constant constant) {
        throw new UnsupportedOperationException("TODO");
    }

    private OutputStream outStream;
    private PrintWriter writer;
}
