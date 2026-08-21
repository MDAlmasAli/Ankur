package ankur.parser.ast;

public record IdentifierExpr(String name, int line, int column) implements Expr {
}
