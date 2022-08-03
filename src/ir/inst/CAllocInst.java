package ir.inst;

import ir.IRVerifyException;
import ir.type.ArrayIRTy;
import ir.type.IRType;
import ir.type.PointerIRTy;

public class CAllocInst extends Instruction {
    public CAllocInst(ArrayIRTy allocType) {
        super(InstKind.CAlloc, IRType.createDecayType(allocType));
        this.allocType = allocType;
    }

    public ArrayIRTy getAllocType() {
        return allocType;
    }

    public int getAllocTypeNum() {
        return allocType.getElementNum();
    }

    public int getAllocSize() {
        return allocType.getSize();
    }

    @Override
    public PointerIRTy getType() {
        final var type = super.getType();
        if (type instanceof PointerIRTy) {
            return (PointerIRTy) type;
        } else {
            verifyFail("Type of CAlloc must be a pointer type");
            return null;
        }
    }

    @Override
    public void verify() throws IRVerifyException {
        super.verify();

        // 类型检查不做了, 不可能出错的 (
    }

    private final ArrayIRTy allocType;
}
