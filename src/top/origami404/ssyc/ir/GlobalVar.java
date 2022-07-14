package top.origami404.ssyc.ir;

import top.origami404.ssyc.ir.constant.ArrayConst;
import top.origami404.ssyc.ir.constant.Constant;
import top.origami404.ssyc.ir.type.IRType;
import top.origami404.ssyc.ir.type.PointerIRTy;

public class GlobalVar extends Value {
    public static GlobalVar createGlobalVariable(IRType varType, String name, Constant init) {
        return new GlobalVar(IRType.createPtrTy(varType), name, init);
    }

    public static GlobalVar createGlobalArray(IRType arrPtr, String name, ArrayConst init) {
        return new GlobalVar(IRType.createPtrTy(arrPtr), name, init);
    }

    @Override
    public PointerIRTy getType() {
        return (PointerIRTy) super.getType();
    }

    public Constant getInit() {
        return init;
    }

    @Override
    public void verify() throws IRVerifyException {
        ensure(getName().charAt(0) == '@',
                "Name of global variable must begin with '@'");
    }

    GlobalVar(IRType type, String name, Constant init) {
        super(type);
        super.setName(name);
        this.init = init;
    }

    private final Constant init;
}
