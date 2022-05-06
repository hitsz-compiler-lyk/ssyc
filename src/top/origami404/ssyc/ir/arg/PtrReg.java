package top.origami404.ssyc.ir.arg;

public class PtrReg extends Argument {
    public static PtrReg newIntPtr(String name) {
        return new PtrReg(Kind.Int, name, 4);
    }

    public static PtrReg newFloatPtr(String name) {
        return new PtrReg(Kind.Float, name, 8);
    }

    public static PtrReg newSamePtr(String name, PtrReg other) {
        return new PtrReg(other.getKind(), name, other.size);
    }

    public static PtrReg newIntArray(String name, int elementCount) {
        return new PtrReg(Kind.Int, name, 4 * elementCount);
    }

    public static PtrReg newFloatArray(String name, int elementCount) {
        return new PtrReg(Kind.Float, name, 8 * elementCount);
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "$" + name;
    }

    private PtrReg(Kind valKind, String name, int size) {
        super(valKind);
        assert valKind.isValue();

        this.name = name;
        this.size = size;
    }

    private String name;
    private int size;
}
