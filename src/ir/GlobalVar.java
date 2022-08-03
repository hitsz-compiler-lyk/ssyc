package ir;

import frontend.SourceCodeSymbol;
import ir.constant.ArrayConst;
import ir.constant.Constant;
import ir.type.IRType;
import ir.type.PointerIRTy;

public class GlobalVar extends Value {
    public static GlobalVar createGlobalVariable(IRType varType, SourceCodeSymbol symbol, Constant init) {
        return new GlobalVar(IRType.createPtrTy(varType), symbol, init);
    }

    public static GlobalVar createGlobalArray(IRType arrPtr, SourceCodeSymbol symbol, ArrayConst init) {
        return new GlobalVar(IRType.createPtrTy(arrPtr), symbol, init);
    }

    @Override
    public PointerIRTy getType() {
        return (PointerIRTy) super.getType();
    }

    public Constant getInit() {
        return init;
    }

    public boolean isVariable() {
        return !isArray();
    }

    public boolean isArray() {
        return getType().getBaseType().isPtr();
    }

    @Override
    public void verify() throws IRVerifyException {
        ensure(getSymbolOpt().isPresent(), "Global variable must own a symbol");
    }

    GlobalVar(IRType type, SourceCodeSymbol symbol, Constant init) {
        super(type);
        super.setSymbol(symbol);
        this.init = init;
    }

    private final Constant init;
}
