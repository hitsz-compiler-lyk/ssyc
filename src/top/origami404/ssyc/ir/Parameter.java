package top.origami404.ssyc.ir;

import top.origami404.ssyc.frontend.SourceCodeSymbol;
import top.origami404.ssyc.ir.type.IRType;

public class Parameter extends Value {
    public Parameter(SourceCodeSymbol symbol, IRType paramType) {
        super(paramType);
        super.setSymbol(symbol);

        this.paramType = paramType;
    }

    public String getParamName() {
        return getSymbol().getName();
    }

    public IRType getParamType() {
        return paramType;
    }

    private final IRType paramType;
}