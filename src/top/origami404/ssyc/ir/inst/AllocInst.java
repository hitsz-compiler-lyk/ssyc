package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.type.IRType;
import top.origami404.ssyc.ir.type.PointerIRTy;

public class AllocInst extends Instruction {
    public AllocInst(IRType allocBaseType) {
        super(InstKind.Alloc, IRType.createPtrTy(allocBaseType));
    }

    public int getAllocSize() {
        PointerIRTy ptrTy = (PointerIRTy) getType();
        assert ptrTy != null : "AllocInst must have a pointer type";

        return ptrTy.getBaseType().getSize();
    }
}