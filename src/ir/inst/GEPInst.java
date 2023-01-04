package ir.inst;

import ir.IRVerifyException;
import ir.Value;
import ir.constant.IntConst;
import ir.type.ArrayIRTy;
import ir.type.IRType;
import ir.type.IRTypeException;
import ir.type.PointerIRTy;
import utils.Log;

import java.util.List;

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
    public List<Value> getIndices() {
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

    public static PointerIRTy calcResultType(IRType originalType, int indexCount) {
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

    @Override
    public void verify() throws IRVerifyException {
        super.verify();

        // Ptr 的类型必须要么是一个指针, 要么是一个数组
        final var ptrType = getPtr().getType();
        ensure(ptrType.isPtr() || ptrType.isArray(), "Ptr of GEP must be a pointer or an array");

        var currType = ptrType;
        for (final var index : getIndices()) {
            ensure(index.getType().isInt(), "Indices of GEP must be a Int");

            Integer indexConst = null;
            if (index instanceof IntConst) {
                indexConst = ((IntConst) index).getValue();
                ensure(indexConst >= 0, "Constant index of GEP must be positive");
            }

            if (currType instanceof PointerIRTy) {
                // ensure(indexConst == null || indexConst == 0,
                //         "Constant index over a pointer must be exactly zero")
                currType = ((PointerIRTy) currType).getBaseType();
            } else if (currType instanceof ArrayIRTy) {
                final var arrayType = (ArrayIRTy) currType;

                // functional/84_long_array2 中包含对 int[1024][4] 类型的数组访问 [0][7] 的行为
                // 这非常不好, 非常非常不好, 有可能导致内存分析失效, 但是鉴于罕见情况勉强忍受一下
                if (!(indexConst == null || indexConst < arrayType.getElementNum())) {
                    Log.info("!!!!!! Cross barry array access !!!!!!!!!!");
                }
                // ensure(indexConst == null || indexConst < arrayType.getElementNum(),
                //         "Constant index over an array must be in range");

                currType = arrayType.getElementType();
            } else {
                verifyFail("Ptr of GEP must be either pointer or an array in every level");
            }
        }

        // functional/61_sort_test7.sy:43
        // 存在将数组的一部分传给函数的情况!
        // ensure(currType.isInt() || currType.isFloat(),
        //         "Shape of ptr of GEP must match the length of indices")
        ensure(currType.equals(getType().getBaseType()), "The final baseType of ptr must match the type of GEP");
    }
}
