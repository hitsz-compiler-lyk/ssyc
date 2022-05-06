package top.origami404.ssyc.ir.type;

import java.util.List;

public class ArrayType implements Type {
    public ArrayType(Type baseType, int size) {
        this.baseType = baseType;
        this.size = size;
    }

    /**
     * 根据 sizes 数组递归构建数组类型. 如果 sizes 为空，则返回基类型
     * @param baseType 基类型
     * @param sizes 各维度的大小
     * @return 数组类型
     */
    public static Type toArrayType(Type baseType, List<Integer> sizes) {
        return sizes.stream()
            .reduce(baseType, ArrayType::new, (a, b) -> a);
    }

    @Override
    public boolean canBeAssignedTo(Type other) {
        // 数组之间永远不能相互赋值
        return false;
    }

    /**
     * 获得数组当前维度的大小 (当前最内层的中括号中的数)
     * @return
     */
    public int getSize() {
        return size;
    }

    /**
     * 获得数组的基类型 (只去掉一层中括号)
     * @return 基类型
     */
    public Type getBaseType() {
        return baseType;
    }

    /**
     * 获得数组最终的元素类型 (去掉所有中括号)
     * @return 最终的元素类型, IntType 或是 FloatType
     */
    public BType getFinalBType() {
        if (baseType instanceof BType bt) {
            return bt;
        } else if (baseType instanceof ArrayType at) {
            return at.getFinalBType();
        } else {
            assert false : "Base type of an array should be a BType or another array";
            return null;
        }
    }

    /**
     * 获得数组的维度 (即有多少个方括号)
     * @return 数组的维度
     */
    public int getDim() {
        if (baseType instanceof ArrayType at) {
            return at.getDim() + 1;
        } else {
            return 0;
        }
    }

    private Type baseType;
    private int size;
}
