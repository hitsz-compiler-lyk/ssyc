package top.origami404.ssyc.ir.inst;

import java.util.Optional;

import top.origami404.ssyc.ir.arg.VarReg;

public class ReturnInst extends Inst {
    public ReturnInst() {
        super(Kind.Return, null, null, null);
    }

    public ReturnInst(VarReg val) {
        super(Kind.Return, null, val, null);
    }

    public Optional<VarReg> getVal() {
        if (arg1 == null) {
            return Optional.empty();
        } else {
            return Optional
                .of(castTo(arg1, VarReg.class));
        }
    }   
}
