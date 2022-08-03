package pass.ir.memory;

import ir.Value;
import ir.constant.ArrayConst;
import ir.constant.Constant;
import ir.inst.Instruction;
import utils.Log;

import java.util.HashMap;
import java.util.Map;

class MemHandler {
    MemHandler() {
        setUndef();
    }

    MemHandler(Instruction insruction) {
        setValue(insruction);
    }

    MemHandler(Constant constant) {
        if (constant instanceof ArrayConst) {
            this.value = null;
            this.elements = new HashMap<>();

            final var elms = ((ArrayConst) constant).getRawElements();
            final var size = elms.size();
            for (int i = 0; i < size; i++) {
                final var handler = new MemHandler(elms.get(i));
                elements.put(i, handler);
            }

        } else {
            this.value = constant;
            this.elements = null;
        }
    }

    public MemHandler get(int idx) {
        Log.ensure(!isVariable(), "get can only be called on array (or undef)");
        if (elements == null) {
            Log.ensure(value == null);
            elements = new HashMap<>();
        }

        elements.computeIfAbsent(idx, i -> new MemHandler());
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
        if (obj instanceof MemHandler) {
            final var handler = (MemHandler) obj;
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

    public boolean canMerge(MemHandler other) {
        // 有一个是 Undef 或者两个的类型相同就可以合并
        return this.isUndef() || other.isUndef()
            || this.isArray() && other.isArray()
            || this.isVariable() && other.isVariable();
    }

    public static MemHandler merge(MemHandler lhs, MemHandler rhs) {
        if (lhs.isUndef() || rhs.isUndef()) {
            return new MemHandler();
        } else if (lhs.isArray() && rhs.isArray()) {
            final var commonKeys = lhs.elements.keySet();
            commonKeys.retainAll(rhs.elements.keySet());

            final var elements = new HashMap<Integer, MemHandler>();
            for (final var key : commonKeys) {
                final var lhsValue = lhs.elements.get(key);
                final var rhsValue = rhs.elements.get(key);
                elements.put(key, merge(lhsValue, rhsValue));
            }

            return new MemHandler(null, elements);
        } else if (lhs.isVariable() && rhs.isVariable()) {
            final var lhsValue = lhs.getValue();
            final var rhsValue = rhs.getValue();
            return lhsValue == rhsValue ? new MemHandler(lhsValue, null) : new MemHandler();
        } else {
            throw new RuntimeException("Cannot merge two handler");
        }
    }

    private MemHandler(Value value, Map<Integer, MemHandler> elements) {
        this.value = value;
        this.elements = elements;
    }

    public MemHandler copy() {
        return new MemHandler(value, new HashMap<>(elements));
    }

    private Map<Integer, MemHandler> elements;
    private Value value;
}
