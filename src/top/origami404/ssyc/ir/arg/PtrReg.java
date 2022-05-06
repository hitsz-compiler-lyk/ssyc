package top.origami404.ssyc.ir.arg;

public class PtrReg extends Argument {
    public PtrReg(Kind valKind) {
        super(valKind);
        assert valKind.isValue();
    }
}
