package top.origami404.ssyc.ir.visitor;

import top.origami404.ssyc.ir.inst.*;

public interface InstructionVisitor<T> {
    default T visit(Instruction inst) {
        if (inst instanceof BinaryOpInst) { return visitBinaryOpInst((BinaryOpInst) inst); }
        if (inst instanceof BrCondInst) { return visitBrCondInst((BrCondInst) inst); }
        if (inst instanceof CallInst) { return visitCallInst((CallInst) inst); }
        if (inst instanceof CmpInst) { return visitCmpInst((CmpInst) inst); }
        if (inst instanceof GEPInst) { return visitGEPInst((GEPInst) inst); }
        if (inst instanceof LoadInst) { return visitLoadInst((LoadInst) inst); }
        if (inst instanceof PhiInst) { return visitPhiInst((PhiInst) inst); }
        if (inst instanceof StoreInst) { return visitStoreInst((StoreInst) inst); }
        if (inst instanceof BoolToIntInst) { return visitBoolToIntInst((BoolToIntInst) inst); }
        if (inst instanceof BrInst) { return visitBrInst((BrInst) inst); }
        if (inst instanceof CAllocInst) { return visitCAllocInst((CAllocInst) inst); }
        if (inst instanceof FloatToIntInst) { return visitFloatToIntInst((FloatToIntInst) inst); }
        if (inst instanceof IntToFloatInst) { return visitIntToFloatInst((IntToFloatInst) inst); }
        if (inst instanceof MemInitInst) { return visitMemInitInst((MemInitInst) inst); }
        if (inst instanceof ReturnInst) { return visitReturnInst((ReturnInst) inst); }
        if (inst instanceof UnaryOpInst) { return visitUnaryOpInst((UnaryOpInst) inst); }
        throw new RuntimeException("Unknown class: " + inst.getClass().getSimpleName());
    }

    T visitBinaryOpInst(BinaryOpInst inst);
    T visitBrCondInst(BrCondInst inst);
    T visitCallInst(CallInst inst);
    T visitCmpInst(CmpInst inst);
    T visitGEPInst(GEPInst inst);
    T visitLoadInst(LoadInst inst);
    T visitPhiInst(PhiInst inst);
    T visitStoreInst(StoreInst inst);
    T visitBoolToIntInst(BoolToIntInst inst);
    T visitBrInst(BrInst inst);
    T visitCAllocInst(CAllocInst inst);
    T visitFloatToIntInst(FloatToIntInst inst);
    T visitIntToFloatInst(IntToFloatInst inst);
    T visitMemInitInst(MemInitInst inst);
    T visitReturnInst(ReturnInst inst);
    T visitUnaryOpInst(UnaryOpInst inst);
}
