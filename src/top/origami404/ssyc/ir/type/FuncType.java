package top.origami404.ssyc.ir.type;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FuncType implements Type {
    public FuncType(List<Type> paramTypes, BType returnType) {
        this.paramTypes = paramTypes;
        this.returnType = returnType;
    }

    public FuncType(BType returnType) {
        this(new ArrayList<>(), returnType);
    }
    
    @Override
    public String toString() {
        return MessageFormat.format(
            "({0}) -> {1}",
            paramTypes.stream()
                .map(Type::toString)
                .collect(Collectors.joining(", ")),
            returnType
        );
    }

    public void addParamType(Type type) {
        paramTypes.add(type);
    }

    public List<Type> getParamTypes() {
        return paramTypes;
    }

    public Type getParamType(int index) {
        return paramTypes.get(index);
    }

    public BType getReturnType() {
        return returnType;
    }

    @Override
    public boolean canBeAssignedTo(Type rhsType) {
        // 函数之间永远不能互相赋值
        return false;
    }

    private List<Type> paramTypes;
    private BType returnType;
}
