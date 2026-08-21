package ankur.parser.ast;

// initializer is null when the declaration has no "= expression" part.
public record VarDeclStmt(VarType type, String name, Expr initializer, int line, int column) implements Stmt {
}
