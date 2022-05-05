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

    public Inst(Kind kind) {
        this.kind = kind;
    }

    /**
     * 获得指令的类型
     * @return 指令的类型
     */
    public Kind getKind() {
        return kind;
    }

    private final Kind kind;


    class ArithInst extends Inst {
        /**
         * 构建只有一个参数的算术指令
         * @param opKind 算术指令类型, INeg 或 FNeg
         * @param dest 结果
         * @param arg1 参数
         */
        public ArithInst(Kind opKind, VarReg dest, Value arg1) {
            this(opKind, dest, arg1, null);
        }

        /**
         * 构建有两个参数的算术指令
         * @param opKind 算术指令类型, 可以是非 INeg 或 FNeg
         * @param dest 结果
         * @param arg1 左边的参数
         * @param arg2 右边的参数
         */
        public ArithInst(Kind opKind, VarReg dest, Value arg1, Value arg2) {
            super(kind);
            this.dest = dest;
            this.arg1 = arg1;
            this.arg2 = Optional.ofNullable(arg2);

            assert arg2 != null || opKind.isOneArgOp();
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
        /**
         * 从内存加载值到寄存器
         * @param dest 目标寄存器
         * @param ptr 内存地址
         */
        public LoadInst(VarReg dest, PtrReg ptr) {
            super(Kind.Load);
            this.dest = dest;
            this.ptr = ptr;

            assert dest.getKind() == ptr.getKind();
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
        /**
         * 将值存入指针所指内存中
         * @param ptr 目标内存地址
         * @param val 值
         */
        public StoreInst(PtrReg ptr, Value val) {
            super(Kind.Store);
            this.ptr = ptr;
            this.val = val;

            assert ptr.getKind() == val.getKind();
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
            this(ptr, switch (ptr.getKind()) {
                case Int    -> 4;
                case Float  -> 8;
                default -> throw new RuntimeException("Cannot ignore alloc size of an array ptr");
            });
        }

        public AllocInst(PtrReg ptr, int size) {
            super(Kind.Alloc);
            this.ptr = ptr;
            this.size = size;

            assert ptr.getKind() == Argument.Kind.Array;
        }

        public PtrReg getPtr() {
            return ptr;
        }

        public int getSize() {
            return size;
        }

        private PtrReg ptr;
        private int size;
    }

    class CompareInst extends Inst {
        public CompareInst(Value left, Value right) {
            super(Kind.CMP);
            this.left = left;
            this.right = right;
        }

        public Value getLeft() {
            return left;
        }

        public Value getRight() {
            return right;
        }

        private Value left;
        private Value right;
    }

    class BranchInst extends Inst {
        /**
         * 无条件跳转
         * @param to 跳转目标
         */
        public BranchInst(BBlock to) {
            this(Kind.Br, to, null);
        }

        /**
         * 普通跳转
         * @param brKind 跳转类型 (Beq, Bne, ...)
         * @param trueBlock 条件为 True 的跳转目的地
         * @param falseBlock 条件为 False 的跳转目的地
         */
        public BranchInst(Kind brKind, BBlock trueBlock, BBlock falseBlock) {
            super(brKind);
            this.trueBlock = trueBlock;
            this.falseBlock = Optional.ofNullable(falseBlock);

            assert falseBlock != null || brKind == Kind.Br;
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