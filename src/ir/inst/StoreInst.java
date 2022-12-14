package ir.inst;

import ir.IRVerifyException;
import ir.Value;
import ir.type.IRType;
import ir.type.IRTypeException;
import ir.type.PointerIRTy;

public class StoreInst extends Instruction {
    public StoreInst(Value ptr, Value val) {
        super(InstKind.Store, IRType.VoidTy);

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
                "Type of an argument of Store must be a pointer to Int or Float, now: " + baseType);
    }
}
