package ankur.parser.ast;

public record WhileStmt(Expr condition, BlockStmt body, int line, int column) implements Stmt {
}
