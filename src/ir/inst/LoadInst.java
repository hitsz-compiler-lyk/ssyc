package ir.inst;

import ir.GlobalVar;
import ir.IRVerifyException;
import ir.Value;
import ir.type.PointerIRTy;

import java.util.Optional;

public class LoadInst extends Instruction {
    public LoadInst(Value ptr) {
        super(
            InstKind.Load,
            Optional.ofNullable((PointerIRTy) ptr.getType())
                .map(PointerIRTy::getBaseType)
                .orElseThrow(() -> new RuntimeException("Argument of load instruction must be a pointer"))
        );

        super.addOperandCO(ptr);
    }

    public Value getPtr() {
        return getOperand(0);
    }

    @Override
    public void verify() throws IRVerifyException {
        super.verify();

        final var type = getType();
        ensure(type.isInt() || type.isFloat() || getPtr() instanceof GlobalVar,
                "Type of load must be Int or Float, or it load a GlobalVar");

        final var ptrType = getPtr().getType();
        ensure(ptrType instanceof PointerIRTy, "Type of an argument of Load must be a pointer");
    }
}
