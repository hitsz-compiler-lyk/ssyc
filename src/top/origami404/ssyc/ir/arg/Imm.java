package top.origami404.ssyc.ir.arg;

public class Imm extends Value {
    Imm(double val) {
        super(Kind.Float);
        this.floatVal = val;
        this.intVal = 0;
    }

    Imm(int val) {
        super(Kind.Int);
        this.intVal = val;
        this.floatVal = 0;
    }

    public int getIntVal() {
        assert this.getKind() == Kind.Int;
        return intVal;
    }

    public double getFloatVal() {
        assert this.getKind() == Kind.Float;
        return floatVal;
    }

    private final double floatVal;
    private final int intVal;
}
