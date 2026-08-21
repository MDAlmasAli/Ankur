package ankur.parser.ast;

public sealed interface Stmt permits VarDeclStmt, AssignStmt, BlockStmt, IfStmt, WhileStmt, PrintStmt {
    int line();

    int column();
}
