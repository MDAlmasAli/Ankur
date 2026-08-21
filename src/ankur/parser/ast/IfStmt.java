package ankur.parser.ast;

// elseBranch is null when there is no নাহলে part.
public record IfStmt(Expr condition, BlockStmt thenBranch, BlockStmt elseBranch, int line, int column)
        implements Stmt {
}
