package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.BasicBlock;
import top.origami404.ssyc.ir.IRVerifyException;
import top.origami404.ssyc.ir.type.ArrayIRTy;
import top.origami404.ssyc.ir.type.IRType;
import top.origami404.ssyc.ir.type.PointerIRTy;

public class CAllocInst extends Instruction {
    public CAllocInst(BasicBlock block, ArrayIRTy allocType) {
        super(block, InstKind.CAlloc, IRType.createDecayType(allocType));
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
