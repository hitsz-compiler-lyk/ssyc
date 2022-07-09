package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.type.IRType;
import top.origami404.ssyc.ir.type.IRTypeException;
import top.origami404.ssyc.ir.type.PointerIRTy;

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
}
