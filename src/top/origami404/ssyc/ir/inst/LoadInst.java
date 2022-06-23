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

        this.ptr = ptr;
        super.addOperandCO(ptr);
    }

    public Value getPtr() {
        return ptr;
    }

    // TODO: 只有 AllocInst 才能是 PointerType, 所以要不要改成细化类型?
    private Value ptr;
}
