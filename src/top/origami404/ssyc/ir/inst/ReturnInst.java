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
        this.returnVal = Optional.ofNullable(returnVal);
    }

    public Optional<Value> getReturnValue() {
        return returnVal;
    }

    private Optional<Value> returnVal;
}
