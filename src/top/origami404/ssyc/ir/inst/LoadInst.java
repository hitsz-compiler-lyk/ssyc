package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.arg.PtrReg;
import top.origami404.ssyc.ir.arg.VarReg;

public class LoadInst extends Inst {
    public LoadInst(VarReg dstVar, PtrReg srcPtr) {
        super(Kind.Load, dstVar, srcPtr, null);
        assert dstVar.getKind() == srcPtr.getKind();
    }

    public VarReg getDstVar() { return castTo(dest, VarReg.class); }
    public PtrReg getSrcPtr() { return castTo(arg1, PtrReg.class); }
}