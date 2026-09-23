package ankur.parser.ast;

public sealed interface Expr permits NumberLiteral, StringLiteral, IdentifierExpr, BinaryExpr, UnaryExpr {
    int line();

    int column();
}
