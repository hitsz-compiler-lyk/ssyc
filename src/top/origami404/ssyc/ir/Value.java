package top.origami404.ssyc.ir;

public class Value implements Argument {
    enum Kind {
        Int, Float
    }

    public Value(Kind kind) {
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

    private final Kind kind;
}
