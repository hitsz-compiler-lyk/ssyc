package top.origami404.ssyc.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ChainMap<K, V> {

    public ChainMap() {
        this(null);
    }

    public ChainMap(ChainMap<K, V> parent) {
        this.map = new HashMap<>();
        this.parent = Optional.ofNullable(parent);
    }

    public void put(K name, V value) {
        map.put(name, value);
    }

    public Optional<V> get(K name) {
        return getInCurr(name)
            .or(() -> parent.flatMap(c -> c.get(name)));
    }

    public Optional<V> getInCurr(K name) {
        return Optional.ofNullable(map.get(name));
    }

    public Optional<ChainMap<K, V>> getParent() {
        return parent;
    }

    private final Map<K, V> map;
    private final Optional<ChainMap<K, V>> parent;
}
