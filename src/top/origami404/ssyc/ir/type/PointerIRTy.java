package top.origami404.ssyc.ir.type;

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
    public boolean equals(Object obj) {
        if (obj instanceof PointerIRTy p) {
            return baseType.equals(p.baseType);
        }

        return false;
    }

    private IRType baseType;
}
