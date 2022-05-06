package top.origami404.ssyc.ir.arg;

public abstract class Value extends Argument {
    public Value(Kind kind) {
        super(kind);
        assert kind.isValue();
    }
}
