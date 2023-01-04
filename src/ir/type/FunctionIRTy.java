package ir.type;

import java.util.List;
import java.util.stream.Collectors;

public class FunctionIRTy implements IRType {
    @Override
    public IRTyKind getKind() {
        return IRTyKind.Function;
    }

    FunctionIRTy(IRType returnType, List<IRType> paramTypes) {
        this.returnType = returnType;
        this.paramTypes = paramTypes;
    }

    @Override
    public int getSize() {
        throw new RuntimeException("Function type dont have size");
    }

    public IRType getReturnType() {
        return returnType;
    }

    public List<IRType> getParamTypes() {
        return paramTypes;
    }

    public IRType getParamType(int idx) {
        return paramTypes.get(idx);
    }

    @Override
    public String toString() {
        final var params = paramTypes.stream().map(IRType::toString).collect(Collectors.toList());
        return "(%s) -> %s".formatted(String.join(", ", params), returnType);
    }

    private final IRType returnType;
    private final List<IRType> paramTypes;
}
