package top.origami404.ssyc.ir.inst;

import java.util.Optional;
import java.util.Set;

import top.origami404.ssyc.ir.arg.Argument;

public class Inst {
    public enum Kind {
        IAdd, ISub, IMul, IDiv, IMod, INeg,
        FAdd, FSub, FMul, FDiv, FNeg,

        Alloc, Load, Store,
        Call, Param, GetArg, Return,

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

    /**
     * 构造 IR 指令
     * @param kind 指令类型
     * @param dest 目标参数 (可空)
     * @param arg1 操作数1 (可空)
     * @param arg2 操作数2 (可空)
     */
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

    protected final Kind kind;
    protected Optional<Argument> dest;
    protected Optional<Argument> arg1;
    protected Optional<Argument> arg2;
}