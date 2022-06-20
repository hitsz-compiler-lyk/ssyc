package top.origami404.ssyc.ir;

import top.origami404.ssyc.ir.type.FunctionIRTy;
import top.origami404.ssyc.utils.IList;
import top.origami404.ssyc.utils.IListOwner;

public class Function extends Value implements IListOwner<BasicBlock, Function> {
    // TODO: Add paramters infos.
    public Function(FunctionIRTy funcType) {
        super(funcType);
    }

    @Override
    public FunctionIRTy getType() {
        assert super.getType() instanceof FunctionIRTy;
        return (FunctionIRTy)super.getType();
    }

    @Override
    public IList<BasicBlock, Function> getIList() {
        return bblocks;
    }

    private IList<BasicBlock, Function> bblocks;
}
