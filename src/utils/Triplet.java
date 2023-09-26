package utils;

import java.io.Serializable;
import java.util.Objects;

public class Triplet<A, B, C> implements Serializable {
    public final A value0;
    public final B value1;
    public final C value2;

    public Triplet(A a, B b, C c) {
        this.value0 = a;
        this.value1 = b;
        this.value2 = c;
    }

    public A getValue0() {
        return value0;
    }

    public B getValue1() {
        return value1;
    }

    public C getValue2() {
        return value2;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof Triplet<?, ?, ?>)) {
            return false;
        }

        Triplet<?, ?, ?> other = (Triplet<?, ?, ?>) obj;
        return other.getValue0().equals(this.value0)
                && other.getValue1().equals(this.value1)
                && other.getValue2().equals(this.value2);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value0, value1, value2);
    }

    @Override
    public String toString() {
        return String.format("(%s, %s, %s)", value0, value1, value2);
    }
}
