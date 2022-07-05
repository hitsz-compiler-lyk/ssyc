package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.type.IRType;

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
}
