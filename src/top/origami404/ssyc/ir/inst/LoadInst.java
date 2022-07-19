package top.origami404.ssyc.ir.inst;

import java.util.Optional;

import top.origami404.ssyc.ir.BasicBlock;
import top.origami404.ssyc.ir.GlobalVar;
import top.origami404.ssyc.ir.IRVerifyException;
import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.type.IRType;
import top.origami404.ssyc.ir.type.PointerIRTy;

public class LoadInst extends Instruction {
    public LoadInst(BasicBlock block, Value ptr) {
        super(
            block,
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

        // assert ptrType instanceof PointerIRTy;
        // final var baseType = ((PointerIRTy) ptrType).getBaseType();
        // ensure(baseType.isInt() || baseType.isFloat(),
        //         "Type of an argument of Load must be a pointer to Int or Float");
    }
}
