package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.arg.Argument;
import top.origami404.ssyc.ir.arg.PtrReg;

public class AllocInst extends Inst {
    public AllocInst(PtrReg ptr) {
        this(ptr, switch (ptr.getKind()) {
            case Int    -> 4;
            case Float  -> 8;
            default -> throw new RuntimeException("Cannot ignore alloc size of an array ptr");
        });
    }

    public AllocInst(PtrReg ptr, int size) {
        super(Kind.Alloc, ptr, null, null);
        assert ptr.getKind() == Argument.Kind.Array;
    }

    public PtrReg getPtr() { return castTo(dest, PtrReg.class); }

    public int getSize() { return size; }
    private int size;
}