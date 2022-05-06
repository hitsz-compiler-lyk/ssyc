package top.origami404.ssyc.ir.type;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class SymTab {
    
    public SymTab() {
        this(null);
    }

    public SymTab(SymTab parent) {
        this.table = new HashMap<>();
        this.parent = Optional.ofNullable(parent);
    }

    public void put(String name, Type type) {
        table.put(name, type);
    }

    public Optional<Type> get(String name) {
        return Optional
            .ofNullable(table.get(name))
            .or(() -> {
                return parent.flatMap(p -> p.get(name));
            });
    }

    public Optional<SymTab> getParent() {
        return parent;
    }

    private Map<String, Type> table;
    private Optional<SymTab> parent;
}
