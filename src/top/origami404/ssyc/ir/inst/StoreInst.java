package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.arg.PtrReg;
import top.origami404.ssyc.ir.arg.Value;

public class StoreInst extends Inst {
    public StoreInst(PtrReg dstPtr, Value srcVal) {
        super(Kind.Store, dstPtr, srcVal, null);
        assert dstPtr.getKind() == srcVal.getKind();
    }

    public PtrReg getDstPtr() { return castTo(dest, PtrReg.class); }
    public Value  getSrcVal() { return castTo(arg1, Value.class);  }
}