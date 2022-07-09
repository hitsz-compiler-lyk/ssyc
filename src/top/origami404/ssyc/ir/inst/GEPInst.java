package top.origami404.ssyc.ir.inst;

import java.awt.*;
import java.util.List;

import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.type.ArrayIRTy;
import top.origami404.ssyc.ir.type.IRType;
import top.origami404.ssyc.ir.type.IRTypeException;
import top.origami404.ssyc.ir.type.PointerIRTy;

public class GEPInst extends Instruction {
    // indices: index 的复数形式
    public GEPInst(Value ptr, List<? extends Value> indices) {
        super(InstKind.GEP, calcResultType(ptr.getType(), indices.size()));

        super.addOperandCO(ptr);
        super.addAllOperandsCO(indices);
    }

    public Value getPtr() {
        return getOperand(0);
    }
    public List<? extends Value> getIndices() {
        return getOperands().subList(1, getOperandSize());
    }
    public Value getIndex(int idx) {
        return getIndices().get(idx);
    }

    @Override
    public PointerIRTy getType() {
        final var type = super.getType();
        if (type instanceof PointerIRTy) {
            return (PointerIRTy) type;
        } else {
            throw new IRTypeException(this, "Type of a GEP must be a pointer type");
        }
    }

    private static IRType calcResultType(IRType originalType, int indexCount) {
        while (indexCount --> 0) {
            if (originalType instanceof ArrayIRTy) {
                final var arrayType = (ArrayIRTy) originalType;
                originalType = arrayType.getElementType();

            } else if (originalType instanceof PointerIRTy) {
                final var ptrTy = (PointerIRTy) originalType;
                originalType = ptrTy.getBaseType();

            } else {
                throw new RuntimeException("Cannot preform GEP on non-array or non-pointer type");
            }
        }

        return IRType.createPtrTy(originalType);
    }
}
