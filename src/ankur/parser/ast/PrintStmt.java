package ankur.parser.ast;

public record PrintStmt(Expr value, int line, int column) implements Stmt {
}
