package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.IRVerifyException;
import top.origami404.ssyc.ir.type.ArrayIRTy;
import top.origami404.ssyc.ir.type.IRType;
import top.origami404.ssyc.ir.type.IRTypeException;
import top.origami404.ssyc.ir.type.PointerIRTy;
import top.origami404.ssyc.utils.Log;

public class AllocInst extends Instruction {
    public AllocInst(IRType allocBaseType) {
        super(InstKind.Alloc, IRType.createPtrTy(allocBaseType));
    }

    public int getAllocSize() {
        return getType().getBaseType().getSize();
    }

    @Override
    public PointerIRTy getType() {
        final var type = super.getType();
        if (type instanceof PointerIRTy) {
            return (PointerIRTy) type;
        } else {
            throw new IRTypeException(this, "Type of Alloc must be a pointer type");
        }
    }

    @Override
    public void verify() throws IRVerifyException {
        super.verify();

        final var type = super.getType();
        ensure(type instanceof PointerIRTy, "Type of an Alloc must be a pointer type");

        assert type instanceof PointerIRTy;
        final var ptrBaseType = ((PointerIRTy) type).getBaseType();
        ensure(ptrBaseType instanceof ArrayIRTy, "Type of an Alloc must be a pointer to an array");
    }
}
