package top.origami404.ssyc.ir.inst;

import java.util.Optional;

import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.type.PointerIRTy;

public class LoadInst extends Instruction {
    public LoadInst(Value ptr) {
        super(
            InstKind.Load,
            Optional.ofNullable((PointerIRTy) ptr.getType())
                .map(PointerIRTy::getBaseType)
                .orElseThrow(() -> new RuntimeException("Argument of load instrution must be a pointer"))
        );

        super.addOperandCO(ptr);
    }

    public Value getPtr() {
        return getOperand(0);
    }
}
