package frontend;

import ir.type.IRType;
import org.antlr.v4.runtime.tree.TerminalNode;
import utils.ChainMap;

import java.util.Optional;

public class SymbolTable {
    public void pushScope() {
        table = new ChainMap<>(table);
    }

    public void popScope() {
        table = table.getParent().orElseThrow(() -> new RuntimeException("Reach scope top"));
    }

    public SourceCodeSymbol resolveSymbol(String name) {
        return resolve(name).symbol();
    }

    public IRType resolveType(String name) {
        return resolve(name).type();
    }

    public Entry resolve(final String name) {
        return resolveOpt(name).orElseThrow(() -> new RuntimeException("Unknown name: " + name));
    }

    public Optional<SourceCodeSymbol> resolveSymbolOpt(String name) {
        return resolveOpt(name).map(Entry::symbol);
    }

    public Optional<IRType> resolveTypeOpt(String name) {
        return resolveOpt(name).map(Entry::type);
    }

    public Optional<Entry> resolveOpt(String name) {
        return table.get(name);
    }

    public void add(String name, SourceCodeSymbol symbol, IRType type) {
        table.put(name, new Entry(symbol, type));
    }

    public void add(SourceCodeSymbol symbol, IRType type) {
        add(symbol.getName(), symbol, type);
    }

    public void add(TerminalNode terminalNode, IRType type) {
        add(new SourceCodeSymbol(terminalNode), type);
    }

    public boolean has(String name) {
        return resolveOpt(name).isPresent();
    }

    public boolean hasInScope(String name) {
        return table.getInCurr(name).isPresent();
    }

    public record Entry(SourceCodeSymbol symbol, IRType type) {}

    private ChainMap<String, Entry> table = new ChainMap<>();
}
