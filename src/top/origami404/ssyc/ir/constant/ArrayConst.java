package top.origami404.ssyc.ir.constant;

import java.util.List;

import top.origami404.ssyc.ir.type.IRType;

/**
 * <p>  数组常量, 一般用于表示数组的初始化器
 *
 * <p>  其构成为:
 * <pre> [ (IntConst | FloatConst)... (ZeroArrayConst)? ] | [ (ArrayConst)... ] </pre>
 *
 * <p>  保证其 {@code elements} 内的所有常量都是相同类型 (比如均为 IntConst,
 *      或者是均为 IntTy 的 ArrayConst). 当 {@code elements} 均为 ArrayConst 时,
 *      确保其逻辑意义上的 "长度" 相同
 *
 * <p>  例子: (ArrayConst ==> 对应的 C 语言中的数组初始化器)
 * <pre> (AC == ArrayConst, ZA == ZeroArrayConst)
 *      AC([1, 2, 3]) ==> {1, 2, 3}
 *      AC([AC([1, ZA(2)]), AC([1, 2, 3])]) ==> {{1}, {1, 2, 3}}
 */
public class ArrayConst extends Constant {
    ArrayConst(List<Constant> elements) {
        super(getTypeFromElements(elements));
        this.elements = elements;
    }

    private static IRType getTypeFromElements(List<Constant> elms) {
        assert elms.size() > 0;

        final var type = elms.get(0).getType();

        assert type.equals(IRType.IntTy) || type.equals(IRType.FloatTy);
        assert elms.stream().allMatch(e -> e.getType().equals(type));

        return type;
    }

    private List<Constant> elements;

    public List<Constant> getRawElements() {
        return elements;
    }

    /**
     * @return 该数组常量逻辑上的长度
     */
    public int getElementNum() {
        final var size = elements.size();
        final var last = elements.get(size);

        if (last instanceof ZeroArrayConst zac) {
            // -1 是为了去掉 ZeroArrayConst 占的那一个
            return size - 1 + zac.getElementNum();
        } else {
            return size;
        }
    }

    /**
     * @param idx 索引
     * @return 该数组常量在逻辑意义上的, 位于该索引处的元素值
     */
    public Constant getElement(int idx) {
        final var size = elements.size();
        final var last = elements.get(size);

        if (last instanceof ZeroArrayConst zac) {
            final var nonZeroEnd = size - 1;
            final var zeroEnd = nonZeroEnd + zac.getElementNum();

            if (0 <= idx && idx < nonZeroEnd) {
                return elements.get(idx);
            } else if (nonZeroEnd <= idx && idx < zeroEnd) {
                return Constant.getZeroByType(zac.getType());
            } else {
                throw new IndexOutOfBoundsException(idx);
            }
        } else {
            return elements.get(idx);
        }
    }

    /**
     * 纯零数组常量
     */
    public static class ZeroArrayConst extends ArrayConst {
        private ZeroArrayConst(IRType type, int length) {
            super(type);
            this.length = length;
        }

        @Override
        public int getElementNum() {
            return length;
        }

        @Override
        public Constant getElement(int idx) {
            return Constant.getZeroByType(getType());
        }

        private int length;
    }

    public static ZeroArrayConst createZeroArrayConst(IRType type, int length) {
        return new ZeroArrayConst(type, length);
    }

    // Only for subclass
    protected ArrayConst(IRType type) {
        super(type);
        this.elements = List.of();
    }
}
