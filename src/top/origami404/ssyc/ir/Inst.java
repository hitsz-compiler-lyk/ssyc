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
        Beq, Bne, Bgt, Bge, Blt, Ble, Br,

        ;

        public boolean isIntOp() {
            final var s = Set.of(IAdd, ISub, IMul, IDiv, IMod, INeg);
            return s.contains(this);
        }

        public boolean isFloatOp() {
            final var s = Set.of(FAdd, FSub, FMul, FDiv, FNeg);
            return s.contains(this);
        }

        public boolean isOneArgOp() {
            final var s = Set.of(INeg, FNeg);
            return s.contains(this);
        }
    }

    public Inst(Kind kind) {
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }

    private final Kind kind;


    class ArithInst extends Inst {
        public ArithInst(Kind opKind, VarReg dest, Value arg1) {
            this(opKind, dest, arg1, null);
        }

        public ArithInst(Kind opKind, VarReg dest, Value arg1, Value arg2) {
            super(kind);
            this.dest = dest;
            this.arg1 = arg1;
            this.arg2 = Optional.ofNullable(arg2);

            assert arg2 != null || arg2 == null && opKind.isOneArgOp();
            assert (opKind.isIntOp() && dest.isInt() && arg1.isInt() 
                    && this.arg2.map(Value::isInt).orElse(false))
                || (opKind.isFloatOp() && dest.isFloat() && arg2.isFloat() 
                    && this.arg2.map(Value::isFloat).orElse(false));
        }

        public VarReg getDest() {
            return dest;
        }

        public Value getArg1() {
            return arg1;
        }

        public Value getArg2() {
            assert !arg2.isEmpty();
            return arg2.get();
        }

        private final VarReg dest;
        private final Value arg1;
        private final Optional<Value> arg2;
    }

    class LoadInst extends Inst {
        public LoadInst(VarReg dest, PtrReg ptr) {
            super(Kind.Load);
            this.dest = dest;
            this.ptr = ptr;
        }

        public VarReg getDest() {
            return dest;
        }

        public PtrReg getPtr() {
            return ptr;
        }

        private VarReg dest;
        private PtrReg ptr;
    }

    class StoreInst extends Inst {
        public StoreInst(PtrReg ptr, Value val) {
            super(Kind.Store);
            this.ptr = ptr;
            this.val = val;
        }

        public PtrReg getPtr() {
            return ptr;
        }

        public Value getVal() {
            return val;
        }

        private PtrReg ptr;
        private Value val;
    }

    class AllocInst extends Inst {
        public AllocInst(PtrReg ptr) {
            super(Kind.Alloc);
            this.ptr = ptr;
        }

        public PtrReg getPtr() {
            return ptr;
        }

        private PtrReg ptr;
    }

    class BranchInst extends Inst {
        public BranchInst(BBlock to) {
            this(Kind.Br, to, null);
        }

        public BranchInst(Kind brKind, BBlock trueBlock, BBlock falseBlock) {
            super(brKind);
            this.trueBlock = trueBlock;
            this.falseBlock = Optional.ofNullable(falseBlock);
        }

        public BBlock getTrueBlock() {
            return trueBlock;
        }

        public BBlock getFalseBlock() {
            assert !falseBlock.isEmpty();
            return falseBlock.get();
        }

        public BBlock getToBlock() {
            assert kind == Kind.Br;
            return trueBlock;
        }

        private BBlock trueBlock;
        private Optional<BBlock> falseBlock;
    }

    class PhiInst extends Inst {
        public PhiInst(VarReg v1, VarReg v2) {
            super(Kind.Phi);
            this.v1 = v1;
            this.v2 = v2;
        }

        public VarReg getV1() {
            return v1;
        }

        public VarReg getV2() {
            return v2;
        }

        private VarReg v1;
        private VarReg v2;
    }
}