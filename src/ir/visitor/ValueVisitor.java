package ir.visitor;

import ir.*;
import ir.inst.Instruction;
import ir.constant.Constant;

public interface ValueVisitor<T> {
    default T visit(Value value) {
        if (value instanceof BasicBlock) { return visitBasicBlock((BasicBlock) value); }
        if (value instanceof Function) { return visitFunction((Function) value); }
        if (value instanceof GlobalVar) { return visitGlobalVar((GlobalVar) value); }
        if (value instanceof Parameter) { return visitParameter((Parameter) value); }
        if (value instanceof Instruction) { return visitInstruction((Instruction) value); }
        if (value instanceof Constant) { return visitConstant((Constant) value); }
        throw new RuntimeException("Unknown class: " + value.getClass().getSimpleName());
    }

    T visitBasicBlock(BasicBlock value);
    T visitFunction(Function value);
    T visitGlobalVar(GlobalVar value);
    T visitParameter(Parameter value);
    T visitInstruction(Instruction value);
    T visitConstant(Constant value);
}
