package ir.inst;

import ir.IRVerifyException;
import ir.Value;
import ir.constant.ArrayConst;
import ir.type.IRType;
import ir.type.PointerIRTy;
import utils.Log;

public class MemInitInst extends Instruction {
    // 由于 MemInit 的特殊性, 很多时候在它创建的时候, 初始值都还没构造好
    // 所以需要一个没有 init 的构造函数, 待会再补上 init
    public MemInitInst(Value arrPtr) {
        this(arrPtr, null);
    }

    public MemInitInst(Value arrPtr, ArrayConst init) {
        super(InstKind.MemInit, IRType.VoidTy);

        super.addOperandCO(arrPtr);
        if (init != null) {
            super.addOperandCO(init);
        }
    }

    public Value getArrayPtr() {
        return getOperand(0);
    }

    public ArrayConst getInit() {
        Log.ensure(getOperandSize() > 1);
        return getOperand(1).as(ArrayConst.class);
    }

    public void setInit(ArrayConst init) {
        if (getOperandSize() > 1) {
            replaceOperandCO(1, init);
        } else {
            addOperandCO(init);
        }
    }

    @Override
    public void verify() throws IRVerifyException {
        super.verify();

        final var arrPtrType = getArrayPtr().getType();
        ensure(arrPtrType instanceof PointerIRTy, "ArrayPtr of a MemInit must be a pointer type");

        final var arrPtrBaseType = ((PointerIRTy) arrPtrType).getBaseType();
        final var initBaseType = getInit().getType().getElementType();
        ensure(arrPtrBaseType.equals(initBaseType),
                "Init and ArrayPtr of a MemInit must share the same base type");
    }
}
