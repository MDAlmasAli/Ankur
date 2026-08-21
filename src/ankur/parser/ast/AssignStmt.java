package ankur.parser.ast;

public record AssignStmt(String name, Expr value, int line, int column) implements Stmt {
}
