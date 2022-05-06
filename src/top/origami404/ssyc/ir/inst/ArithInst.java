package top.origami404.ssyc.ir.inst;

import top.origami404.ssyc.ir.arg.Value;
import top.origami404.ssyc.ir.arg.VarReg;

public class ArithInst extends Inst {
    public ArithInst(Kind opKind, VarReg result, Value left) {
        super(opKind, result, left, null);
        assert opKind.isOneArgOp();
    }

    public ArithInst(Kind opKind, VarReg result, Value left, Value right) {
        super(opKind, result, left, right);

        assert !opKind.isOneArgOp();
        assert (opKind.isIntOp() && result.isInt() && left.isInt() && right.isInt())
            || (opKind.isFloatOp() && result.isFloat() && left.isFloat() && right.isFloat());
    }

    public VarReg getResult() { return castTo(dest, VarReg.class); }
    public Value  getLeft()   { return castTo(arg1, Value.class);  }
    public Value  getRight()  { return castTo(arg2, Value.class);  }
}