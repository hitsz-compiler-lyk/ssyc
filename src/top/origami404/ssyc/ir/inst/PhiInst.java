package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.arg.VarReg;

public class PhiInst extends Inst {
    public PhiInst(VarReg merged, VarReg v1, VarReg v2) {
        super(Kind.Phi, merged, v1, v2);
    }

    public VarReg getMerged() { return castTo(dest, VarReg.class); }
    public VarReg getV1()     { return castTo(arg1, VarReg.class); }
    public VarReg getV2()     { return castTo(arg2, VarReg.class); }
}