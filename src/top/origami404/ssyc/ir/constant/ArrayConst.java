package top.origami404.ssyc.ir.constant;

import java.util.List;

import top.origami404.ssyc.ir.type.ArrayIRTy;
import top.origami404.ssyc.ir.type.IRType;
import top.origami404.ssyc.utils.Log;

/**
 * <p>数组常量, 一般用于表示数组的初始化器
 *
 * <p>其构成为:
 * <pre> [ (IntConst | FloatConst)... ] | [ (ArrayConst)... ] | ZeroArrayConst </pre>
 *
 * <p>保证其 {@code elements} 内的所有常量都是相同类型 (比如均为 IntConst,
 * 或者是均为 IntTy 的 ArrayConst).
 *
 * <p>例子: (ArrayConst ==> 对应的 C 语言中的数组初始化器, AC == ArrayConst, ZA == ZeroArrayConst)
 * <pre>
 * AC([1, 2, 3]) ==> {1, 2, 3}
 * AC([AC([1, 0, 0]), AC([1, 2, 3]), ZA]) ==> {{1}, {1, 2, 3}, {}}
 * AC([1, 0, 0, ...... 0]) ==> {1}
 * </pre>
 *
 * <p>考虑一个 <pre> int a[100] = {1} </pre>, 如例三. 这种情况下, 对于我们的实现,
 * 只能往 ArrayConst 里塞一个长度为 100 的大 List. 对 LLVM 而言, 它会
 * 将数组改写为聚合类型 <{ i32, [99 x i32] }>, 随后使用
 * <{ i32 0, [99 x i32] zeroinitializer }> 来初始化. 鉴于我们没有实现
 * 聚合类型, 而且像上面这种情况在测试代码里非常少见, 我们最终还是选择了
 * 简单的实现.
 */
public class ArrayConst extends Constant {
    ArrayConst(List<Constant> elements) {
        super(getTypeFromElements(elements));
        this.elements = elements;
    }

    private static IRType getTypeFromElements(List<Constant> elms) {
        Log.ensure(elms.size() > 0);

        final var type = elms.get(0).getType();

        Log.ensure(type.equals(IRType.IntTy) || type.equals(IRType.FloatTy));
        Log.ensure(elms.stream().allMatch(e -> e.getType().equals(type)));

        return IRType.createArrayTy(elms.size(), type);
    }

    private List<Constant> elements;

    public List<Constant> getRawElements() {
        return elements;
    }

    public void addZeroTo(int targetSize) {
        final var elmTy = ((ArrayIRTy) getType()).getElementType();
        final var zero = Constant.getZeroByType(elmTy);

        while (elements.size() < targetSize) {
            elements.add(zero);
        }
    }

    /**
     * 纯零数组常量
     */
    public static class ZeroArrayConst extends ArrayConst {
        public ZeroArrayConst(IRType type) {
            super(type);
        }
    }

    // Only for subclass
    protected ArrayConst(IRType type) {
        super(type);
        this.elements = List.of();
    }
}
