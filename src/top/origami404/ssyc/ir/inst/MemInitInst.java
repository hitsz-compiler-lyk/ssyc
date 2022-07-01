package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.constant.ArrayConst;
import top.origami404.ssyc.ir.type.ArrayIRTy;
import top.origami404.ssyc.ir.type.IRType;

public class MemInitInst extends Instruction {
    public MemInitInst(Value array, ArrayConst init) {
        super(InstKind.MemInit, IRType.VoidTy);
        assert array.getType() instanceof ArrayIRTy;

        this.array = array;
        this.init = init;

        super.addOperandCO(array);
    }

    public Value getArray() {
        return array;
    }

    public ArrayConst getInit() {
        return init;
    }

    private Value array;
    private ArrayConst init;
}
