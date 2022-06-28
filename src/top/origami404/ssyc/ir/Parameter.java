package top.origami404.ssyc.ir;

import top.origami404.ssyc.ir.type.IRType;

public class Parameter extends Value {
    public Parameter(String paramName, IRType paramType) {
        super(paramType);

        this.name = paramName;
        this.paramType = paramType;
    }

    public String getName() {
        return name;
    }

    public IRType getParamType() {
        return paramType;
    }

    private String name;
    private IRType paramType;
}