package top.origami404.ssyc.ir.inst;

import java.util.Optional;

import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.type.IRType;

public class ReturnInst extends Instruction {
    public ReturnInst() {
        this(null);
    }

    public ReturnInst(Value returnVal) {
        super(InstKind.Ret, IRType.VoidTy);
        if (returnVal != null) {
            addOperandCO(returnVal);
        }
    }

    public Optional<Value> getReturnValue() {
        if (getOperandSize() > 0) {
            return Optional.of(getOperand(0));
        } else {
            return Optional.empty();
        }
    }
}
