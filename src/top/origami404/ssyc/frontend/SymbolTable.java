package top.origami404.ssyc.frontend;

import org.antlr.v4.runtime.tree.TerminalNode;
import top.origami404.ssyc.ir.type.IRType;
import top.origami404.ssyc.utils.ChainMap;

import java.util.Optional;

public class SymbolTable {
    public void pushScope() {
        table = new ChainMap<>(table);
    }

    public void popScope() {
        table = table.getParent().orElseThrow(() -> new RuntimeException("Reach scope top"));
    }

    public SourceCodeSymbol resolveSymbol(String name) {
        return resolve(name).getSymbol();
    }

    public IRType resolveType(String name) {
        return resolve(name).getType();
    }

    public Entry resolve(final String name) {
        return resolveOpt(name).orElseThrow(() -> new RuntimeException("Unknown name: " + name));
    }

    public Optional<SourceCodeSymbol> resolveSymbolOpt(String name) {
        return resolveOpt(name).map(Entry::getSymbol);
    }

    public Optional<IRType> resolveTypeOpt(String name) {
        return resolveOpt(name).map(Entry::getType);
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

    public static class Entry {
        public Entry(SourceCodeSymbol symbol, IRType type) {
            this.symbol = symbol;
            this.type = type;
        }

        public SourceCodeSymbol getSymbol() {
            return symbol;
        }

        public IRType getType() {
            return type;
        }

        final SourceCodeSymbol symbol;
        final IRType type;
    }

    private ChainMap<String, Entry> table = new ChainMap<>();
}
