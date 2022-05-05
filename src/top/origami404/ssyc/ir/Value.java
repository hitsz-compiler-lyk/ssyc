package top.origami404.ssyc.ir;

public class Value extends Argument {
    public Value(Kind kind) {
        super(kind);
        assert kind.isValue();
    }
}
