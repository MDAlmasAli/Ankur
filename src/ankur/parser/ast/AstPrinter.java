package ankur.parser.ast;

import java.util.ArrayList;
import java.util.List;

// Renders the parsed syntax tree as an indented box-drawing tree, the way a directory listing
// is drawn. Statements become nodes; expressions are printed inline in their original infix
// form (with the literals exactly as they were written in the source, Bangla numerals
// included) because a fully expanded expression tree buries the shape of the program.
public final class AstPrinter {

    private AstPrinter() {
    }

    public static String print(Program program) {
        StringBuilder sb = new StringBuilder();
        sb.append("PROGRAM\n");
        printStatements(program.statements(), "", sb);
        return sb.toString();
    }

    private static void printStatements(List<Stmt> statements, String prefix, StringBuilder sb) {
        for (int i = 0; i < statements.size(); i++) {
            printStmt(statements.get(i), prefix, i == statements.size() - 1, sb);
        }
    }

    private static void printStmt(Stmt stmt, String prefix, boolean isLast, StringBuilder sb) {
        switch (stmt) {
            case VarDeclStmt v -> {
                node(prefix, isLast, "DECLARATION", sb);
                List<String> fields = new ArrayList<>();
                fields.add("TYPE  : " + typeName(v.type()));
                fields.add("NAME  : " + v.name());
                if (v.initializer() != null) {
                    fields.add("VALUE : " + exprText(v.initializer()));
                }
                leaves(childPrefix(prefix, isLast), fields, sb);
            }
            case AssignStmt a -> {
                node(prefix, isLast, "ASSIGNMENT", sb);
                leaves(childPrefix(prefix, isLast), List.of(
                        "NAME  : " + a.name(),
                        "VALUE : " + exprText(a.value())), sb);
            }
            case PrintStmt p -> {
                node(prefix, isLast, "PRINT (দেখাও)", sb);
                leaves(childPrefix(prefix, isLast), List.of(
                        "VALUE : " + exprText(p.value())), sb);
            }
            case IfStmt i -> {
                node(prefix, isLast, "IF (যদি)", sb);
                String childPrefix = childPrefix(prefix, isLast);
                boolean hasElse = i.elseBranch() != null;
                leaf(childPrefix, false, "CONDITION : " + exprText(i.condition()), sb);
                node(childPrefix, !hasElse, "THEN", sb);
                printStatements(i.thenBranch().statements(), childPrefix(childPrefix, !hasElse), sb);
                if (hasElse) {
                    node(childPrefix, true, "ELSE (নাহলে)", sb);
                    printStatements(i.elseBranch().statements(), childPrefix(childPrefix, true), sb);
                }
            }
            case WhileStmt w -> {
                node(prefix, isLast, "WHILE (যতক্ষণ)", sb);
                String childPrefix = childPrefix(prefix, isLast);
                leaf(childPrefix, false, "CONDITION : " + exprText(w.condition()), sb);
                node(childPrefix, true, "BODY", sb);
                printStatements(w.body().statements(), childPrefix(childPrefix, true), sb);
            }
            case BlockStmt b -> {
                node(prefix, isLast, "BLOCK", sb);
                printStatements(b.statements(), childPrefix(prefix, isLast), sb);
            }
        }
    }

    private static void leaves(String prefix, List<String> fields, StringBuilder sb) {
        for (int i = 0; i < fields.size(); i++) {
            leaf(prefix, i == fields.size() - 1, fields.get(i), sb);
        }
    }

    private static void node(String prefix, boolean isLast, String label, StringBuilder sb) {
        sb.append(prefix).append(isLast ? "└── " : "├── ").append(label).append('\n');
    }

    private static void leaf(String prefix, boolean isLast, String text, StringBuilder sb) {
        node(prefix, isLast, text, sb);
    }

    private static String childPrefix(String prefix, boolean isLast) {
        return prefix + (isLast ? "    " : "│   ");
    }

    // Expressions print back in infix form. NumberLiteral.text() is the lexeme as written, so
    // a source that used ২০ shows ২০ here rather than 20.
    public static String exprText(Expr expr) {
        return switch (expr) {
            case NumberLiteral n -> n.text();
            case StringLiteral s -> s.text();
            case IdentifierExpr id -> id.name();
            case UnaryExpr u -> symbolFor(u.op()) + exprText(u.operand());
            case BinaryExpr b -> "(" + exprText(b.left()) + " " + symbolFor(b.op()) + " " + exprText(b.right()) + ")";
        };
    }

    private static String typeName(VarType type) {
        return switch (type) {
            case PURNO -> "পূর্ণ";
            case DOSHOMIK -> "দশমিক";
            case BAKKO -> "বাক্য";
        };
    }

    private static String symbolFor(BinaryOp op) {
        return switch (op) {
            case ADD -> "+";
            case SUB -> "-";
            case MUL -> "*";
            case DIV -> "/";
            case MOD -> "%";
            case EQ -> "==";
            case NEQ -> "!=";
            case LT -> "<";
            case GT -> ">";
            case LE -> "<=";
            case GE -> ">=";
            case AND -> "&&";
            case OR -> "||";
        };
    }

    private static String symbolFor(UnaryOp op) {
        return switch (op) {
            case NEG -> "-";
            case POS -> "+";
            case NOT -> "!";
        };
    }
}
