package top.origami404.ssyc.ir;

import java.util.Optional;
import java.util.Set;

public class Inst {
    enum Kind {
        IAdd, ISub, IMul, IDiv, IMod, INeg,
        FAdd, FSub, FMul, FDiv, FNeg,

        Alloc, Load, Store,
        Call, Param, GetArg,

        Phi,
        CMP, Beq, Bne, Bgt, Bge, Blt, Ble, Br,

        ;

        public boolean isIntOp() {
            return Set.of(IAdd, ISub, IMul, IDiv, IMod, INeg).contains(this);
        }

        public boolean isFloatOp() {
            return Set.of(FAdd, FSub, FMul, FDiv, FNeg).contains(this);
        }

        public boolean isOneArgOp() {
            return Set.of(INeg, FNeg).contains(this);
        }
    }

    public Inst(Kind kind, Argument dest, Argument arg1, Argument arg2) {
        this.kind = kind;
        this.dest = Optional.ofNullable(dest);
        this.arg1 = Optional.ofNullable(arg1);
        this.arg2 = Optional.ofNullable(arg2);
    }

    public Kind getKind() { return kind; }
    public Optional<Argument> getDest() { return dest; }
    public Optional<Argument> getArg1() { return arg1; }
    public Optional<Argument> getArg2() { return arg2; }

    protected<T extends Argument> T castTo(Optional<Argument> value, Class<T> cls) {
        return value.map(cls::cast).orElseThrow(() -> new RuntimeException("Cannot cast"));
    }

    private final Kind kind;
    private Optional<Argument> dest;
    private Optional<Argument> arg1;
    private Optional<Argument> arg2;


    class ArithInst extends Inst {
        public ArithInst(Kind opKind, VarReg result, Value left) {
            super(opKind, result, left, null);
            assert opKind.isOneArgOp();
        }

        public ArithInst(Kind opKind, VarReg result, Value left, Value right) {
            super(kind, result, left, right);

            assert !opKind.isOneArgOp();
            assert (opKind.isIntOp() && result.isInt() && left.isInt() && right.isInt())
                || (opKind.isFloatOp() && result.isFloat() && left.isFloat() && right.isFloat());
        }

        public VarReg getResult() { return castTo(dest, VarReg.class); }
        public Value  getLeft()   { return castTo(arg1, Value.class);  }
        public Value  getRight()  { return castTo(arg2, Value.class);  }
    }

    class LoadInst extends Inst {
        public LoadInst(VarReg dstVar, PtrReg srcPtr) {
            super(Kind.Load, dstVar, srcPtr, null);
            assert dstVar.getKind() == srcPtr.getKind();
        }

        public VarReg getDstVar() { return castTo(dest, VarReg.class); }
        public PtrReg getSrcPtr() { return castTo(arg1, PtrReg.class); }
    }

    class StoreInst extends Inst {
        public StoreInst(PtrReg dstPtr, Value srcVal) {
            super(Kind.Store, dstPtr, srcVal, null);
            assert dstPtr.getKind() == srcVal.getKind();
        }

        public PtrReg getDstPtr() { return castTo(dest, PtrReg.class); }
        public Value  getSrcVal() { return castTo(arg1, Value.class);  }
    }

    class AllocInst extends Inst {
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

    class CompareInst extends Inst {
        public CompareInst(Value left, Value right) {
            super(Kind.CMP, null, left, right);
        }

        public Value getLeft()  { return castTo(arg1, Value.class); }
        public Value getRight() { return castTo(arg2, Value.class); }
    }

    class BranchInst extends Inst {
        /**
         * 无条件跳转
         */
        public BranchInst(BBlock to) {
            super(Kind.Br, null, to, null);
        }

        /**
         * 普通跳转
         * @param brKind 跳转类型 (Beq, Bne, ...)
         */
        public BranchInst(Kind brKind, BBlock trueBlock, BBlock falseBlock) {
            super(brKind, null, trueBlock, falseBlock);
        }

        public BBlock getTrueBlock()  { return castTo(arg1, BBlock.class); }
        public BBlock getFalseBlock() { return castTo(arg2, BBlock.class); }
        public BBlock getToBlock()    { return castTo(arg1, BBlock.class); }
    }

    class PhiInst extends Inst {
        public PhiInst(VarReg merged, VarReg v1, VarReg v2) {
            super(Kind.Phi, merged, v1, v2);
        }

        public VarReg getMerged() { return castTo(dest, VarReg.class); }
        public VarReg getV1()     { return castTo(arg1, VarReg.class); }
        public VarReg getV2()     { return castTo(arg2, VarReg.class); }
    }
}