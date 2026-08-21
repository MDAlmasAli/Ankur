package ankur.parser.ast;

import java.util.List;

public final class AstPrinter {

    private AstPrinter() {
    }

    public static String print(Program program) {
        StringBuilder sb = new StringBuilder();
        sb.append("Program\n");
        printStatements(program.statements(), 1, sb);
        return sb.toString();
    }

    private static void printStatements(List<Stmt> statements, int indentLevel, StringBuilder sb) {
        for (Stmt stmt : statements) {
            printStmt(stmt, indentLevel, sb);
        }
    }

    private static void indent(int level, StringBuilder sb) {
        sb.append("  ".repeat(level));
    }

    private static void printStmt(Stmt stmt, int indentLevel, StringBuilder sb) {
        indent(indentLevel, sb);
        switch (stmt) {
            case VarDeclStmt v -> {
                sb.append("VarDecl ").append(v.type()).append(' ').append(v.name()).append('\n');
                if (v.initializer() != null) {
                    printExpr(v.initializer(), indentLevel + 1, sb);
                }
            }
            case AssignStmt a -> {
                sb.append("Assign ").append(a.name()).append('\n');
                printExpr(a.value(), indentLevel + 1, sb);
            }
            case IfStmt i -> {
                sb.append("If\n");
                indent(indentLevel + 1, sb);
                sb.append("Condition:\n");
                printExpr(i.condition(), indentLevel + 2, sb);
                indent(indentLevel + 1, sb);
                sb.append("Then:\n");
                printStatements(i.thenBranch().statements(), indentLevel + 2, sb);
                if (i.elseBranch() != null) {
                    indent(indentLevel + 1, sb);
                    sb.append("Else:\n");
                    printStatements(i.elseBranch().statements(), indentLevel + 2, sb);
                }
            }
            case WhileStmt w -> {
                sb.append("While\n");
                indent(indentLevel + 1, sb);
                sb.append("Condition:\n");
                printExpr(w.condition(), indentLevel + 2, sb);
                indent(indentLevel + 1, sb);
                sb.append("Body:\n");
                printStatements(w.body().statements(), indentLevel + 2, sb);
            }
            case PrintStmt p -> {
                sb.append("Print\n");
                printExpr(p.value(), indentLevel + 1, sb);
            }
            case BlockStmt b -> {
                sb.append("Block\n");
                printStatements(b.statements(), indentLevel + 1, sb);
            }
        }
    }

    private static void printExpr(Expr expr, int indentLevel, StringBuilder sb) {
        indent(indentLevel, sb);
        switch (expr) {
            case NumberLiteral n -> sb.append("Number ").append(n.text()).append('\n');
            case IdentifierExpr id -> sb.append("Identifier ").append(id.name()).append('\n');
            case BinaryExpr b -> {
                sb.append("Binary ").append(b.op()).append('\n');
                printExpr(b.left(), indentLevel + 1, sb);
                printExpr(b.right(), indentLevel + 1, sb);
            }
            case UnaryExpr u -> {
                sb.append("Unary ").append(u.op()).append('\n');
                printExpr(u.operand(), indentLevel + 1, sb);
            }
        }
    }
}
