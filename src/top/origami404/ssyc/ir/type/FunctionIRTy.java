package top.origami404.ssyc.ir.type;

import java.util.List;

public class FunctionIRTy implements IRType {
    @Override
    public IRTyKind getKind() {
        return IRTyKind.Function;
    }

    FunctionIRTy(IRType returnType, List<IRType> paramTypes) {
        this.returnType = returnType;
        this.paramTypes = paramTypes;
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
    
    private IRType returnType;
    private List<IRType> paramTypes;
}
