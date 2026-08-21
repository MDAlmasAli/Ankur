package ankur.parser.ast;

public sealed interface Expr permits NumberLiteral, IdentifierExpr, BinaryExpr, UnaryExpr {
    int line();

    int column();
}
