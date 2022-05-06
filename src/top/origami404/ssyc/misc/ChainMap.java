package top.origami404.ssyc.misc;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ChainMap<K, V> {
    public ChainMap() {
        this(null);
    }

    public ChainMap(ChainMap<K, V> parent) {
        this.table = new HashMap<>();
        this.parent = Optional.ofNullable(parent);
    }

    public void put(K name, V type) {
        table.put(name, type);
    }

    public Optional<V> get(K name) {
        return Optional
            .ofNullable(table.get(name))
            .or(() -> {
                return parent.flatMap(p -> p.get(name));
            });
    }

    public Optional<ChainMap<K, V>> getParent() {
        return parent;
    }

    private Map<K, V> table;
    private Optional<ChainMap<K, V>> parent;

}
