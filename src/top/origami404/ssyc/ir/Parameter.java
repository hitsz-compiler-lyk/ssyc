package top.origami404.ssyc.ir;

import top.origami404.ssyc.ir.type.IRType;

public class Parameter extends Value {
    public Parameter(String paramName, IRType paramType) {
        super(paramType);
        super.setName(paramName);

        this.paramType = paramType;
    }

    public String getParamName() {
        final var llvmName = getName();
        final var dollarIndex = llvmName.indexOf('$');
        return llvmName.substring(1, dollarIndex);
    }

    public IRType getParamType() {
        return paramType;
    }

    private IRType paramType;
}