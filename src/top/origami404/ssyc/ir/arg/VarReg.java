package top.origami404.ssyc.ir.arg;

public class VarReg extends Value {
    public static VarReg newIntTemp() {
        final Integer thisCount = temporaryCounter++;
        return newIntNamed(thisCount.toString());
    }

    public static VarReg newFloatTemp() {
        final Integer thisCount = temporaryCounter++;
        return newFloatNamed(thisCount.toString());
    }

    public static VarReg newSameTemp(VarReg other) {
        final Integer thisCount = temporaryCounter++;
        return newSameNamed(thisCount.toString(), other);
    }

    public static VarReg newIntNamed(String name) {
        return new VarReg(Kind.Int, name);
    }

    public static VarReg newFloatNamed(String name) {
        return new VarReg(Kind.Float, name);
    }

    public static VarReg newSameNamed(String name, VarReg other) {
        return new VarReg(other.getKind(), name);
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "%" + name;
    }

    private static int temporaryCounter = 0;
    private VarReg(Kind kind, String name) {
        super(kind);
        this.name = name;
    }

    private final String name;
}
