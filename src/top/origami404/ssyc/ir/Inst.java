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

    public Inst(Kind kind, Argument dest, Argument arg1, Argument arg2) {
        this.kind = kind;
        this.dest = Optional.ofNullable(dest);
        this.arg1 = Optional.ofNullable(arg1);
        this.arg2 = Optional.ofNullable(arg2);
    }

    /**
     * 获得指令的类型
     * @return 指令的类型
     */
    public Kind getKind() {
        return kind;
    }

    public Optional<Argument> getDest() {
        return dest;
    }

    public Optional<Argument> getArg1() {
        return arg1;
    }

    public Optional<Argument> getArg2() {
        return arg2;
    }

    protected<T extends Argument> T castTo(Optional<Argument> value, Class<T> cls) {
        return value.map(cls::cast).orElseThrow(() -> new RuntimeException("Cannot cast"));
    }

    private final Kind kind;
    private Optional<Argument> dest;
    private Optional<Argument> arg1;
    private Optional<Argument> arg2;


    class ArithInst extends Inst {
        /**
         * 构建只有一个参数的算术指令
         * @param opKind 算术指令类型, INeg 或 FNeg
         * @param result 结果
         * @param left 参数
         */
        public ArithInst(Kind opKind, VarReg result, Value left) {
            super(opKind, result, left, null);
        }

        /**
         * 构建有两个参数的算术指令
         * @param opKind 算术指令类型, 可以是非 INeg 或 FNeg
         * @param result 结果
         * @param left 左边的参数
         * @param right 右边的参数
         */
        public ArithInst(Kind opKind, VarReg result, Value left, Value right) {
            super(kind, result, left, right);

            assert right != null || opKind.isOneArgOp();
            assert (opKind.isIntOp() && result.isInt() && left.isInt() && right.isInt())
                || (opKind.isFloatOp() && result.isFloat() && left.isFloat() && right.isFloat());
        }

        public VarReg getResult() { return castTo(dest, VarReg.class); }
        public Value  getLeft()   { return castTo(arg1, Value.class);  }
        public Value  getRight()  { return castTo(arg2, Value.class);  }
    }

    class LoadInst extends Inst {
        /**
         * 从内存加载值到寄存器
         * @param dstVar 目标寄存器
         * @param srcPtr 内存地址
         */
        public LoadInst(VarReg dstVar, PtrReg srcPtr) {
            super(Kind.Load, dstVar, srcPtr, null);
            assert dstVar.getKind() == srcPtr.getKind();
        }

        public VarReg getDstVar() { return castTo(dest, VarReg.class); }
        public PtrReg getSrcPtr() { return castTo(arg1, PtrReg.class); }
    }

    class StoreInst extends Inst {
        /**
         * 将值存入指针所指内存中
         * @param dstPtr 目标内存地址
         * @param srcVal 值
         */
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
         * @param to 跳转目标
         */
        public BranchInst(BBlock to) {
            super(Kind.Br, null, to, null);
        }

        /**
         * 普通跳转
         * @param brKind 跳转类型 (Beq, Bne, ...)
         * @param trueBlock 条件为 True 的跳转目的地
         * @param falseBlock 条件为 False 的跳转目的地
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