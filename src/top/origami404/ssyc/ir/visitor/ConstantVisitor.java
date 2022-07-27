package top.origami404.ssyc.ir.visitor;

import top.origami404.ssyc.ir.constant.*;

public interface ConstantVisitor<T> {
    default T visit(Constant constant) {
        if (constant instanceof IntConst) { return visitIntConst((IntConst) constant); }
        if (constant instanceof FloatConst) { return visitFloatConst((FloatConst) constant); }
        if (constant instanceof BoolConst) { return visitBoolConst((BoolConst) constant); }
        if (constant instanceof ArrayConst) { return visitArrayConst((ArrayConst) constant); }
        throw new RuntimeException("Unknown class: " + constant.getClass().getSimpleName());
    }

    T visitIntConst(IntConst constant);
    T visitFloatConst(FloatConst constant);
    T visitBoolConst(BoolConst constant);
    T visitArrayConst(ArrayConst constant);
}
