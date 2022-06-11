package top.origami404.ssyc.ir;

import top.origami404.ssyc.ir.type.FunctionIRTy;

public class Function extends Value {
    // TODO: Add paramters infos.
    public Function(FunctionIRTy funcType) {
        super(funcType);
    }

    @Override
    public FunctionIRTy getType() {
        assert super.getType() instanceof FunctionIRTy;
        return (FunctionIRTy)super.getType();
    }
}
