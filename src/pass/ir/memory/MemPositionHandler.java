package pass.ir.memory;

import ir.Value;
import ir.constant.ArrayConst;
import ir.constant.Constant;
import ir.inst.Instruction;
import utils.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * <h3>记录某个特定内存变量的特定内存位置的值</h3>
 *
 * <p>
 *     设计上是每一个 MemPositionHandler 都有可能是三种状态: Array/Variable/Undef, 分别代表这个 Handler 内是保存着某个数组, 某个变量的具体信息
 *     还是保存着一个未知的值. 当状态是 Array 时, 它就表现为一个 int -> MemPositionHandler 的 Map; 当状态是 Variable 时, 它就表现为存着一个值
 *     的容器.
 * </p>
 * <p>
 *     采用此设计的具体原因是我们需要复用 "找到对应位置" 的部分, 而在找到那个位置之后再选择是 "写入" 还是 "读取". 这就代表我们不能简单地采用
 *     "树状 Map" 的方案, 因为这样当找到对应位置之后的操作不同的时候, 对叶子节点的 Map 的操作就要不同, 从而无法复用 "查找位置" 的操作.
 * </p>
 * <p>
 *     影响该类设计的因素还有对内存空间的节省. 因为实际上在开始做分析的时候, 对运行时某个内存位置的值我们应该是知之甚少的. 因此如果采用像
 *     ArrayConst 一样的方案 (树状结构, 定长数组), 就会导致内存空间的极大浪费. 因此我们在每一层使用 Map 来模拟一个 "稀疏数组"
 * </p>
 */
class MemPositionHandler {
    MemPositionHandler() {
        setUndef();
    }

    MemPositionHandler(Instruction instruction) {
        setValue(instruction);
    }

    MemPositionHandler(Constant constant) {
        if (constant instanceof ArrayConst) {
            this.value = null;
            this.elements = new HashMap<>();

            final var elms = ((ArrayConst) constant).getRawElements();
            final var size = elms.size();
            for (int i = 0; i < size; i++) {
                final var handler = new MemPositionHandler(elms.get(i));
                elements.put(i, handler);
            }

        } else {
            this.value = constant;
            this.elements = null;
        }
    }

    /**
     * 在该 Handler (数组或 Undef 状态)保存的位置的基础上获得对应位置的 MemPositionHandler
     * @param idx 该层的索引
     */
    public MemPositionHandler get(int idx) {
        Log.ensure(!isVariable(), "get can only be called on array (or undef)");

        // 如果自己是 Undef 状态, 那么先切换到空白的 Array 状态
        if (elements == null) {
            Log.ensure(value == null);
            elements = new HashMap<>();
        }

        // 然后返回对应位置的 Handler, 如果对应位置没有 Handler 就返回 Undef
        elements.computeIfAbsent(idx, i -> new MemPositionHandler());
        return elements.get(idx);
    }

    public Value getValue() {
        Log.ensure(isVariable(), "getValue can only be called on variable");
        return value;
    }

    public boolean isUndef() {
        return elements == null && value == null;
    }

    public boolean isArray() {
        return elements != null && value == null;
    }

    public boolean isVariable() {
        return elements == null && value != null;
    }

    public void setValue(Value value) {
        this.elements = null;
        this.value = value;
    }

    public void setUndef() {
        this.elements = null;
        this.value = null;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MemPositionHandler) {
            final var handler = (MemPositionHandler) obj;
            if (this.isArray() && handler.isArray()) {
                return elements.equals(handler.elements);
            } else if (this.isVariable() && handler.isVariable()) {
                return value.equals(handler.value);
            } else {
                return this.isUndef() && handler.isUndef();
            }
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        final var valueHashCode = value == null ? 0 : value.hashCode();
        final var elementsHashCode = elements == null ? 0 : elements.hashCode();
        return valueHashCode * 37 + elementsHashCode;
    }

    public boolean canMerge(MemPositionHandler other) {
        // 有一个是 Undef 或者两个的类型相同就可以合并
        return this.isUndef() || other.isUndef()
            || this.isArray() && other.isArray()
            || this.isVariable() && other.isVariable();
    }

    /**
     * <h3>合并两个 MemPositionHandler</h3>
     * <p>
     *     此处 "合并" 的意思是:
     *     <ul>
     *         <li>对于在相同位置的相同的值, 保留那个值</li>
     *         <li>对于在相同位置的不同的值, 两个都不要, 改成 Undef</li>
     *         <li>不同位置, 不互相干扰</li>
     *     </ul>
     *     这个操作是当一个基本块要从多个前继继承对同一个内存变量的不同 MemPositionHandler 的值时要采取的操作
     * </p>
     */
    public static MemPositionHandler merge(MemPositionHandler lhs, MemPositionHandler rhs) {
        if (lhs.isUndef() || rhs.isUndef()) {
            // 有一个是 Undef 那么结果就是 Undef
            return new MemPositionHandler();

        } else if (lhs.isArray() && rhs.isArray()) {
            // 两个都是 Array 那么就递归合并
            final var commonKeys = lhs.elements.keySet();
            commonKeys.retainAll(rhs.elements.keySet());

            final var elements = new HashMap<Integer, MemPositionHandler>();
            for (final var key : commonKeys) {
                final var lhsValue = lhs.elements.get(key);
                final var rhsValue = rhs.elements.get(key);
                elements.put(key, merge(lhsValue, rhsValue));
            }

            return new MemPositionHandler(null, elements);

        } else if (lhs.isVariable() && rhs.isVariable()) {
            // 两个都是 Variable 那么就当相同时返回值, 不同时返回 Undef
            final var lhsValue = lhs.getValue();
            final var rhsValue = rhs.getValue();
            return lhsValue == rhsValue ? new MemPositionHandler(lhsValue, null) : new MemPositionHandler();

        } else {
            throw new RuntimeException("Cannot merge two handler");
        }
    }

    private MemPositionHandler(Value value, Map<Integer, MemPositionHandler> elements) {
        this.value = value;
        this.elements = elements;
    }

    public MemPositionHandler copy() {
        return new MemPositionHandler(value, new HashMap<>(elements));
    }

    private Map<Integer, MemPositionHandler> elements;
    private Value value;
}
