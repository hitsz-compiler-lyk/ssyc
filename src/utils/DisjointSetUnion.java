package utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class DisjointSetUnion<T> {
    private final Map<T, T> parent;

    public DisjointSetUnion() {
        this.parent = new HashMap<>();
    }

    public T find(T val) {
        if (parent.getOrDefault(val, val).equals(val)) {
            return val;
        } else {
            var root = find(parent.get(val));
            parent.put(val, root);
            return root;
        }
    }

    // x 是父亲
    public void merge(T x, T y) {
        var fx = find(x);
        var fy = find(y);
        parent.put(fy, fx);
    }
}
