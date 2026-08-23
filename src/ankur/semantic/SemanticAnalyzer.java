package ankur.semantic;

import ankur.errors.ErrorReporter;
import ankur.errors.Phase;
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
import ankur.parser.ast.UnaryExpr;
import ankur.parser.ast.UnaryOp;
import ankur.parser.ast.VarDeclStmt;
import ankur.parser.ast.VarType;
import ankur.parser.ast.WhileStmt;

public final class SemanticAnalyzer {

    private final ErrorReporter reporter;
    private SymbolTable scope = new SymbolTable(null);

    public SemanticAnalyzer(ErrorReporter reporter) {
        this.reporter = reporter;
    }

    public void analyze(Program program) {
        for (Stmt stmt : program.statements()) {
            analyzeStmt(stmt);
        }
    }

    private void analyzeStmt(Stmt stmt) {
        switch (stmt) {
            case VarDeclStmt v -> analyzeVarDecl(v);
            case AssignStmt a -> analyzeAssign(a);
            case IfStmt i -> analyzeIf(i);
            case WhileStmt w -> analyzeWhile(w);
            case PrintStmt p -> analyzeExpr(p.value());
            case BlockStmt b -> analyzeBlock(b, new SymbolTable(scope));
        }
    }

    private void analyzeVarDecl(VarDeclStmt v) {
        if (scope.declaredInCurrentScope(v.name())) {
            reporter.report(Phase.SEMANTIC, v.line(), v.column(),
                    "Variable '" + v.name() + "' is already declared in this scope");
        }
        Type declaredType = v.type() == VarType.PURNO ? Type.INT : Type.FLOAT;
        if (v.initializer() != null) {
            Type initType = analyzeExpr(v.initializer());
            checkAssignable(declaredType, initType, v.name(), v.line(), v.column());
        }
        scope.declare(new Symbol(v.name(), declaredType, v.line(), v.initializer() != null));
    }

    private void analyzeAssign(AssignStmt a) {
        Symbol symbol = scope.resolve(a.name());
        if (symbol == null) {
            reporter.report(Phase.SEMANTIC, a.line(), a.column(),
                    "Variable '" + a.name() + "' is not declared");
        }
        Type valueType = analyzeExpr(a.value());
        if (symbol != null) {
            checkAssignable(symbol.type, valueType, a.name(), a.line(), a.column());
            symbol.initialized = true;
        }
    }

    private void analyzeIf(IfStmt i) {
        Type conditionType = analyzeExpr(i.condition());
        if (conditionType != Type.BOOLEAN && conditionType != Type.UNKNOWN) {
            reporter.report(Phase.SEMANTIC, i.condition().line(), i.condition().column(),
                    "The condition of 'যদি' must be a boolean expression (e.g. x > 5), not " + describe(conditionType));
        }
        analyzeBlock(i.thenBranch(), new SymbolTable(scope));
        if (i.elseBranch() != null) {
            analyzeBlock(i.elseBranch(), new SymbolTable(scope));
        }
    }

    private void analyzeWhile(WhileStmt w) {
        Type conditionType = analyzeExpr(w.condition());
        if (conditionType != Type.BOOLEAN && conditionType != Type.UNKNOWN) {
            reporter.report(Phase.SEMANTIC, w.condition().line(), w.condition().column(),
                    "The condition of 'যতক্ষণ' must be a boolean expression (e.g. x < 10), not " + describe(conditionType));
        }
        analyzeBlock(w.body(), new SymbolTable(scope));
    }

    private void analyzeBlock(BlockStmt block, SymbolTable childScope) {
        SymbolTable previous = this.scope;
        this.scope = childScope;
        try {
            for (Stmt stmt : block.statements()) {
                analyzeStmt(stmt);
            }
        } finally {
            this.scope = previous;
        }
    }

    private Type analyzeExpr(Expr expr) {
        return switch (expr) {
            case NumberLiteral n -> {
                // Codegen emits পূর্ণ as a Java `int`; reject here so an out-of-range literal
                // is a clear semantic error instead of a mysterious javac failure downstream.
                if (!n.isFloat() && (n.intValue() < Integer.MIN_VALUE || n.intValue() > Integer.MAX_VALUE)) {
                    reporter.report(Phase.SEMANTIC, n.line(), n.column(),
                            "Integer literal " + n.text() + " is out of range for পূর্ণ (must fit in a 32-bit int)");
                    yield Type.UNKNOWN;
                }
                yield n.isFloat() ? Type.FLOAT : Type.INT;
            }
            case IdentifierExpr id -> analyzeIdentifier(id);
            case BinaryExpr b -> analyzeBinary(b);
            case UnaryExpr u -> analyzeUnary(u);
        };
    }

    private Type analyzeIdentifier(IdentifierExpr id) {
        Symbol symbol = scope.resolve(id.name());
        if (symbol == null) {
            reporter.report(Phase.SEMANTIC, id.line(), id.column(), "Variable '" + id.name() + "' is not declared");
            return Type.UNKNOWN;
        }
        if (!symbol.initialized) {
            reporter.report(Phase.SEMANTIC, id.line(), id.column(),
                    "Variable '" + id.name() + "' is used before being assigned a value");
        }
        return symbol.type;
    }

    private Type analyzeBinary(BinaryExpr b) {
        Type leftType = analyzeExpr(b.left());
        Type rightType = analyzeExpr(b.right());
        if (leftType == Type.UNKNOWN || rightType == Type.UNKNOWN) {
            return Type.UNKNOWN;
        }
        return switch (b.op()) {
            case ADD, SUB, MUL, DIV, MOD -> {
                if (!isNumeric(leftType) || !isNumeric(rightType)) {
                    reporter.report(Phase.SEMANTIC, b.line(), b.column(),
                            "Arithmetic operator '" + symbolFor(b.op()) + "' needs two numbers, got "
                                    + describe(leftType) + " and " + describe(rightType));
                    yield Type.UNKNOWN;
                }
                // int/int division by a literal 0 throws ArithmeticException in the generated
                // Java; float division by 0.0 is well-defined (Infinity/NaN), so only int matters.
                boolean isIntDivOrMod = (b.op() == BinaryOp.DIV || b.op() == BinaryOp.MOD)
                        && leftType == Type.INT && rightType == Type.INT;
                if (isIntDivOrMod && isLiteralIntZero(b.right())) {
                    reporter.report(Phase.SEMANTIC, b.line(), b.column(),
                            "Division by zero: the right-hand side of '" + symbolFor(b.op()) + "' is the literal 0");
                    yield Type.UNKNOWN;
                }
                yield (leftType == Type.FLOAT || rightType == Type.FLOAT) ? Type.FLOAT : Type.INT;
            }
            case EQ, NEQ, LT, GT, LE, GE -> {
                if (!isNumeric(leftType) || !isNumeric(rightType)) {
                    reporter.report(Phase.SEMANTIC, b.line(), b.column(),
                            "Comparison '" + symbolFor(b.op()) + "' needs two numbers, got "
                                    + describe(leftType) + " and " + describe(rightType));
                    yield Type.UNKNOWN;
                }
                yield Type.BOOLEAN;
            }
            case AND, OR -> {
                if (leftType != Type.BOOLEAN || rightType != Type.BOOLEAN) {
                    reporter.report(Phase.SEMANTIC, b.line(), b.column(),
                            "Logical '" + symbolFor(b.op()) + "' needs two boolean expressions (comparisons), got "
                                    + describe(leftType) + " and " + describe(rightType));
                    yield Type.UNKNOWN;
                }
                yield Type.BOOLEAN;
            }
        };
    }

    private Type analyzeUnary(UnaryExpr u) {
        Type operandType = analyzeExpr(u.operand());
        if (operandType == Type.UNKNOWN) {
            return Type.UNKNOWN;
        }
        return switch (u.op()) {
            case NEG, POS -> {
                if (!isNumeric(operandType)) {
                    reporter.report(Phase.SEMANTIC, u.line(), u.column(),
                            "Unary '" + (u.op() == UnaryOp.NEG ? "-" : "+") + "' needs a number, got "
                                    + describe(operandType));
                    yield Type.UNKNOWN;
                }
                yield operandType;
            }
            case NOT -> {
                if (operandType != Type.BOOLEAN) {
                    reporter.report(Phase.SEMANTIC, u.line(), u.column(),
                            "'!' needs a boolean expression (comparison), got " + describe(operandType));
                    yield Type.UNKNOWN;
                }
                yield Type.BOOLEAN;
            }
        };
    }

    private void checkAssignable(Type target, Type valueType, String name, int line, int column) {
        if (valueType == Type.UNKNOWN || target == valueType) {
            return;
        }
        if (target == Type.FLOAT && valueType == Type.INT) {
            return; // widening int -> float is allowed
        }
        String precisionNote = (target == Type.INT && valueType == Type.FLOAT) ? " (this would lose precision)" : "";
        reporter.report(Phase.SEMANTIC, line, column,
                "Cannot assign " + describe(valueType) + " to '" + name + "' which is " + describe(target) + precisionNote);
    }

    private static boolean isNumeric(Type t) {
        return t == Type.INT || t == Type.FLOAT;
    }

    private static boolean isLiteralIntZero(Expr expr) {
        return expr instanceof NumberLiteral n && !n.isFloat() && n.intValue() == 0;
    }

    private static String describe(Type t) {
        return switch (t) {
            case INT -> "পূর্ণ (int)";
            case FLOAT -> "দশমিক (float)";
            case BOOLEAN -> "boolean";
            case UNKNOWN -> "unknown";
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
}
