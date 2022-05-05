package top.origami404.ssyc.ir;

public class VarReg extends Value {
    public static VarReg newIntTemp() {
        final Integer thisCount = temporaryCounter++;
        return new VarReg(Kind.Int, "%" + thisCount.toString());
    }

    public static VarReg newFloatTemp() {
        final Integer thisCount = temporaryCounter++;
        return new VarReg(Kind.Float, "%" + thisCount.toString());
    }

    public static VarReg newIntNamed(String name) {
        return new VarReg(Kind.Int, name);
    }

    public static VarReg newFloatNamed(String name) {
        return new VarReg(Kind.Float, name);
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return getName();
    }

    private static int temporaryCounter = 0;
    private VarReg(Kind kind, String name) {
        super(kind);
        this.name = name;
    }

    private final String name;
}
