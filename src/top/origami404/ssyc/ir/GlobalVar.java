package top.origami404.ssyc.ir;

import top.origami404.ssyc.ir.constant.ArrayConst;
import top.origami404.ssyc.ir.constant.Constant;
import top.origami404.ssyc.ir.type.IRType;

public class GlobalVar extends Value {
    public static GlobalVar createGlobalVariable(IRType varType, String name, Constant init) {
        return new GlobalVar(IRType.createPtrTy(varType), name, init);
    }

    public static GlobalVar createGlobalArray(IRType arrType, String name, ArrayConst init) {
        return new GlobalVar(arrType, name, init);
    }

    public Constant getInit() {
        return init;
    }

    GlobalVar(IRType type, String name, Constant init) {
        super(type);
        super.setName(name);
        this.init = init;
    }

    private final Constant init;
}