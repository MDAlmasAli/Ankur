package ankur.parser.ast;

public record UnaryExpr(UnaryOp op, Expr operand, int line, int column) implements Expr {
}
