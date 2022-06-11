package top.origami404.ssyc.ir.type;

public class SimpleIRTy implements IRType {
    SimpleIRTy(IRTyKind kind) {
        this.kind = kind;
    }

    @Override
    public IRTyKind getKind() {
        return kind;
    }

    private IRTyKind kind;
}
