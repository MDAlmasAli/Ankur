package ankur.semantic;

import java.util.HashMap;
import java.util.Map;

// A scope, chained to its enclosing scope; each block gets its own so declarations shadow rather than collide.
public final class SymbolTable {
    private final SymbolTable parent;
    private final Map<String, Symbol> symbols = new HashMap<>();

    public SymbolTable(SymbolTable parent) {
        this.parent = parent;
    }

    public boolean declaredInCurrentScope(String name) {
        return symbols.containsKey(name);
    }

    public void declare(Symbol symbol) {
        symbols.put(symbol.name, symbol);
    }

    public Symbol resolve(String name) {
        for (SymbolTable scope = this; scope != null; scope = scope.parent) {
            Symbol found = scope.symbols.get(name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
