package ir.type;

public class PointerIRTy implements IRType {
    @Override
    public IRTyKind getKind() {
        return IRTyKind.Pointer;
    }

    PointerIRTy(IRType baseType) {
        this.baseType = baseType;
    }

    public IRType getBaseType() {
        return baseType;
    }

    @Override
    public int getSize() {
        return 4;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof final PointerIRTy ptr) {
            return baseType.equals(ptr.baseType);
        }

        return false;
    }

    @Override
    public String toString() {
        return "*%s".formatted(baseType);
    }

    private final IRType baseType;
}
