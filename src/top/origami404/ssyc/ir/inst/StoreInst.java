package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.type.IRType;

public class StoreInst extends Instruction {
    public StoreInst(Value ptr, Value val) {
        super(InstKind.Store, IRType.VoidTy);

        this.ptr = ptr;
        this.val = val;

        super.addOperandCO(ptr);
        super.addOperandCO(val);
    }

    public Value getPtr() {
        return ptr;
    }

    public Value getVal() {
        return val;
    }

    private Value ptr;
    private Value val;
}
