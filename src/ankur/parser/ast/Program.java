package ankur.parser.ast;

import java.util.List;

public record Program(List<Stmt> statements, int line, int column) {
}
