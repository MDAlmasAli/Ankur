package ankur.parser.ast;

public record BinaryExpr(Expr left, BinaryOp op, Expr right, int line, int column) implements Expr {
}
