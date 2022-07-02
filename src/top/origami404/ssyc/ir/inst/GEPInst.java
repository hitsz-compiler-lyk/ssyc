package top.origami404.ssyc.ir.inst;

import java.util.List;

import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.type.ArrayIRTy;
import top.origami404.ssyc.ir.type.IRType;
import top.origami404.ssyc.ir.type.PointerIRTy;

public class GEPInst extends Instruction {
    // indices: index 的复数形式
    public GEPInst(Value ptr, List<? extends Value> indices) {
        super(InstKind.GEP, calcResultType(ptr.getType(), indices.size()));

        this.ptr = ptr;
        this.indices = indices;

        super.addOperandCO(ptr);
        super.addAllOperandsCO(indices);
    }

    public Value getPtr() {
        return ptr;
    }

    public List<? extends Value> getIndices() {
        return indices;
    }

    public Value getIndex(int idx) {
        return indices.get(idx);
    }

    private Value ptr;
    private List<? extends Value> indices;

    private static IRType calcResultType(IRType originalType, int indexCount) {
        while (indexCount --> 0) {
            if (originalType instanceof ArrayIRTy) {
                final var arrayType = (ArrayIRTy) originalType;
                originalType = arrayType.getElementType();

            } else if (originalType instanceof PointerIRTy) {
                final var ptrTy = (PointerIRTy) originalType;
                originalType = ptrTy.getBaseType();

            } else {
                throw new RuntimeException("Cannot preform GEP on non-array type");
            }
        }

        return originalType;
    }
}
