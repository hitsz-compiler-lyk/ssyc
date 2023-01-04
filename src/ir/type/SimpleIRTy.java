package ir.type;

public class SimpleIRTy implements IRType {
    SimpleIRTy(IRTyKind kind) {
        this(kind, -1);
    }

    SimpleIRTy(IRTyKind kind, int size) {
        this.kind = kind;
        this.size = size;
    }

    @Override
    public IRTyKind getKind() {
        return kind;
    }

    @Override
    public int getSize() {
        if (size < 0) {
            throw new RuntimeException("Simple type " + kind.toString() + " dont have size");
        }

        return size;
    }

    @Override
    public String toString() {
        return kind.toString();
    }

    private final IRTyKind kind;
    private final int size;
}
