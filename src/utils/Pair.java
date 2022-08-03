package utils;

import java.io.Serializable;
import java.util.Objects;

public class Pair<A, B> implements Serializable {
    public final A key;
    public final B value;

    public Pair(A a, B b) {
        this.key = a;
        this.value = b;
    }

    public A getKey() {
        return key;
    }

    public B getValue() {
        return value;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof Pair<?, ?>)) {
            return false;
        }

        Pair<?, ?> other = (Pair<?, ?>) obj;
        return other.getKey().equals(this.key)
                && other.getValue().equals(this.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    @Override
    public String toString() {
        return String.format("(%s, %s)", key, value);
    }
}
