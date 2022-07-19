package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.BasicBlock;
import top.origami404.ssyc.ir.IRVerifyException;
import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.type.IRType;
import top.origami404.ssyc.ir.type.IRTypeException;
import top.origami404.ssyc.ir.type.PointerIRTy;

public class StoreInst extends Instruction {
    public StoreInst(BasicBlock block, Value ptr, Value val) {
        super(block, InstKind.Store, IRType.VoidTy);

        super.addOperandCO(ptr);
        super.addOperandCO(val);
    }

    public Value getPtr() {
        return getOperand(0);
    }
    public Value getVal() {
        return getOperand(1);
    }

    public IRType getPtrBaseType() {
        final var baseType = getPtr().getType();
        if (baseType instanceof PointerIRTy) {
            return ((PointerIRTy) baseType).getBaseType();
        } else {
            throw new IRTypeException(this, "Ptr of StoreInst must have a pointer type");
        }
    }

    @Override
    public void verify() throws IRVerifyException {
        super.verify();

        final var ptrType = getPtr().getType();
        ensure(ptrType instanceof PointerIRTy, "Type of an argument of Store must be a pointer");

        assert ptrType instanceof PointerIRTy;
        final var baseType = ((PointerIRTy) ptrType).getBaseType();
        ensure(baseType.isInt() || baseType.isFloat(),
                "Type of an argument of Store must be a pointer to Int or Float");
    }
}
