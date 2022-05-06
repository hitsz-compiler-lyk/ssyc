package top.origami404.ssyc.ir.arg;

public class Argument {
    public enum Kind {
        Int, Float, Array, BBlock, Function
        ;
        public boolean isValue() {
            return this == Int || this == Float;
        }
    }

    public Argument(Kind kind) {
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }

    public boolean isInt() {
        return getKind() == Kind.Int;
    }

    public boolean isFloat() {
        return getKind() == Kind.Float;
    }

    private Kind kind;
}
