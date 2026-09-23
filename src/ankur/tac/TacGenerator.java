package ankur.tac;

import ankur.parser.ast.AssignStmt;
import ankur.parser.ast.BinaryExpr;
import ankur.parser.ast.BinaryOp;
import ankur.parser.ast.BlockStmt;
import ankur.parser.ast.Expr;
import ankur.parser.ast.IdentifierExpr;
import ankur.parser.ast.IfStmt;
import ankur.parser.ast.NumberLiteral;
import ankur.parser.ast.PrintStmt;
import ankur.parser.ast.Program;
import ankur.parser.ast.Stmt;
import ankur.parser.ast.StringLiteral;
import ankur.parser.ast.UnaryExpr;
import ankur.parser.ast.UnaryOp;
import ankur.parser.ast.VarDeclStmt;
import ankur.parser.ast.WhileStmt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Lowers a validated AST into three-address code: a flat list of instructions where every
// expression has been broken down into single operations over named operands, and every
// যদি/যতক্ষণ has become an explicit label-and-jump.
//
// Nothing downstream currently consumes this list -- both code generators walk the AST, since
// their targets have structured control flow of their own and would only have to rebuild it
// from the jumps. TAC is here because it is the representation an optimiser or a real machine
// backend would need, and printing it makes the flattening visible while the program is still
// small enough to read.
public final class TacGenerator {

    private final List<TacInstr> instructions = new ArrayList<>();

    // Shadowing again: TAC operands are flat names, so an inner ক that shadows an outer one
    // has to be told apart. The first declaration of a name keeps it; later ones get a suffix.
    private final Deque<Map<String, String>> scopes = new ArrayDeque<>();
    private final Map<String, Integer> nameCounts = new HashMap<>();

    private int temporaryCount = 0;
    private int labelCount = 0;

    public List<TacInstr> generate(Program program) {
        scopes.push(new HashMap<>());
        for (Stmt stmt : program.statements()) {
            genStmt(stmt);
        }
        scopes.pop();
        return List.copyOf(instructions);
    }

    // ---- Statements ----

    private void genStmt(Stmt stmt) {
        switch (stmt) {
            case VarDeclStmt v -> {
                // The initializer is evaluated before the name is declared, so that a nested
                // declaration shadowing an outer name still reads the outer one.
                String value = v.initializer() != null ? genExpr(v.initializer()) : "০";
                emit(new TacInstr.Copy(declare(v.name()), value));
            }
            case AssignStmt a -> emit(new TacInstr.Copy(resolve(a.name()), genExpr(a.value())));
            case PrintStmt p -> emit(new TacInstr.Print(genExpr(p.value())));
            case IfStmt i -> genIf(i);
            case WhileStmt w -> genWhile(w);
            case BlockStmt b -> genBlock(b);
        }
    }

    // যদি (c) { A } নাহলে { B }  becomes
    //     ifFalse c goto L1 / A / goto L2 / L1: / B / L2:
    // and without a নাহলে part, simply
    //     ifFalse c goto L1 / A / L1:
    private void genIf(IfStmt i) {
        String condition = genExpr(i.condition());
        String afterThen = newLabel();
        emit(new TacInstr.IfFalseGoto(condition, afterThen));
        genBlock(i.thenBranch());

        if (i.elseBranch() == null) {
            emit(new TacInstr.Label(afterThen));
            return;
        }
        String end = newLabel();
        emit(new TacInstr.Goto(end));
        emit(new TacInstr.Label(afterThen));
        genBlock(i.elseBranch());
        emit(new TacInstr.Label(end));
    }

    // যতক্ষণ (c) { B } becomes
    //     L1: / ifFalse c goto L2 / B / goto L1 / L2:
    // The condition is re-evaluated at the top of every iteration, which is why the label
    // comes before it rather than after.
    private void genWhile(WhileStmt w) {
        String top = newLabel();
        String exit = newLabel();
        emit(new TacInstr.Label(top));
        String condition = genExpr(w.condition());
        emit(new TacInstr.IfFalseGoto(condition, exit));
        genBlock(w.body());
        emit(new TacInstr.Goto(top));
        emit(new TacInstr.Label(exit));
    }

    private void genBlock(BlockStmt block) {
        scopes.push(new HashMap<>());
        for (Stmt stmt : block.statements()) {
            genStmt(stmt);
        }
        scopes.pop();
    }

    // ---- Expressions ----

    // Returns the name holding the expression's value: a literal, a variable, or a fresh
    // temporary into which the operation was computed.
    private String genExpr(Expr expr) {
        return switch (expr) {
            case NumberLiteral n -> n.text();
            case StringLiteral s -> s.text();
            case IdentifierExpr id -> resolve(id.name());
            case UnaryExpr u -> {
                String operand = genExpr(u.operand());
                String dest = newTemporary();
                emit(new TacInstr.UnOp(dest, symbolFor(u.op()), operand));
                yield dest;
            }
            case BinaryExpr b -> {
                // Both sides are evaluated into names first: that flattening is the whole
                // point of three-address code, and it is what makes each line independent.
                String left = genExpr(b.left());
                String right = genExpr(b.right());
                String dest = newTemporary();
                emit(new TacInstr.BinOp(dest, left, symbolFor(b.op()), right));
                yield dest;
            }
        };
    }

    // ---- Names ----

    private void emit(TacInstr instruction) {
        instructions.add(instruction);
    }

    private String newTemporary() {
        return "t" + (++temporaryCount);
    }

    private String newLabel() {
        return "L" + (++labelCount);
    }

    private String declare(String ankurName) {
        int seen = nameCounts.merge(ankurName, 1, Integer::sum);
        String tacName = seen == 1 ? ankurName : ankurName + "@" + seen;
        scopes.peek().put(ankurName, tacName);
        return tacName;
    }

    private String resolve(String ankurName) {
        for (Map<String, String> scope : scopes) {
            String found = scope.get(ankurName);
            if (found != null) {
                return found;
            }
        }
        throw new IllegalStateException(
                "Unresolved identifier '" + ankurName + "' while generating TAC "
                        + "(the program should have been validated by SemanticAnalyzer first)");
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
