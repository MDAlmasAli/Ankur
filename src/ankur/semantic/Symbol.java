package ankur.semantic;

public final class Symbol {
    public final String name;
    public final Type type;
    public final int declaredAtLine;
    // Not flow-sensitive: true as soon as any assignment is seen anywhere, even in a branch that might not run.
    public boolean initialized;

    public Symbol(String name, Type type, int declaredAtLine, boolean initialized) {
        this.name = name;
        this.type = type;
        this.declaredAtLine = declaredAtLine;
        this.initialized = initialized;
    }
}
