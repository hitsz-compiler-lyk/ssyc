package top.origami404.ssyc.ir.type;

public interface Type {
    /**
     * 判断当前具有当前类型的变量能否被具有 rhsType 类型的值赋值
     * @param rhsType 待赋值给该类型变量的值的类型
     * @return 如果可以赋值，返回 true，否则返回 false
     */
    public boolean canBeAssignedTo(Type rhsType);
}
