package top.origami404.ssyc.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ChainMap<T> {

    public ChainMap() {
        this(null);
    }

    public ChainMap(ChainMap<T> parent) {
        this.map = new HashMap<>();
        this.parent = Optional.ofNullable(parent);
    }

    public void put(String name, T value) {
        map.put(name, value);
    }

    public Optional<T> get(String name) {
        return getInCurr(name)
            .or(() -> parent.flatMap(c -> c.get(name)));
    }

    public Optional<T> getInCurr(String name) {
        return Optional.ofNullable(map.get(name));
    }

    public Optional<ChainMap<T>> getParent() {
        return parent;
    }

    private Map<String, T> map;
    private Optional<ChainMap<T>> parent;
}
