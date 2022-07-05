package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.constant.ArrayConst;
import top.origami404.ssyc.ir.type.ArrayIRTy;
import top.origami404.ssyc.ir.type.IRType;
import top.origami404.ssyc.utils.Log;

public class MemInitInst extends Instruction {
    // 由于 MemInit 的特殊性, 很多时候在它创建的时候, 初始值都还没构造好
    // 所以需要一个没有 init 的构造函数, 待会再补上 init
    public MemInitInst(Value array) {
        this(array, null);
    }

    public MemInitInst(Value array, ArrayConst init) {
        super(InstKind.MemInit, IRType.VoidTy);
        Log.ensure(array.getType() instanceof ArrayIRTy);

        this.array = array;
        this.init = init;

        super.addOperandCO(array);
    }

    public Value getArray() {
        return array;
    }

    public ArrayConst getInit() {
        Log.ensure(init != null);
        return init;
    }

    public void setInit(ArrayConst init) {
        this.init = init;
    }

    private Value array;
    private ArrayConst init;
}
