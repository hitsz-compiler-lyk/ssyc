package top.origami404.ssyc.ir.type;

public class ArrayIRTy implements IRType {
    @Override
    public IRTyKind getKind() {
        return IRTyKind.Array;
    }

    ArrayIRTy(int elementNum, IRType elementType) {
        this.elementNum = elementNum;
        this.elementType = elementType;
    }

    /**
     * @return 数组的长度 (元素数量)
     */
    public int getElementNum() {
        return elementNum;
    }

    /**
     * @return 数组的基本类型
     */
    public IRType getElementType() {
        return elementType;
    }

    @Override
    public int getSize() {
        return elementNum * elementType.getSize();
    }

    // TODO: 也许可以考虑在任何情况下都返回 false
    // 因为实际上两个数组类型本身就不能互操作
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ArrayIRTy) {
            final var at = (ArrayIRTy) obj;
            return elementNum == at.elementNum
                && elementType.equals(at.elementType);
        }

        return false;
    }

    private int elementNum;
    private IRType elementType;
}
