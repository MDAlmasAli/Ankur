package ankur.parser.ast;

import java.util.List;

public record BlockStmt(List<Stmt> statements, int line, int column) implements Stmt {
}
