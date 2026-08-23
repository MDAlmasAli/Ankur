package ankur.parser;

import ankur.errors.ErrorReporter;
import ankur.errors.Phase;
import ankur.lexer.Token;
import ankur.lexer.TokenType;
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

import java.util.ArrayList;
import java.util.List;

// Hand-written recursive-descent parser. Grammar: docs/grammar.bnf.
public final class Parser {

    private static final class ParseError extends RuntimeException {
    }

    private final List<Token> tokens;
    private final ErrorReporter reporter;
    private int current = 0;

    public Parser(List<Token> tokens, ErrorReporter reporter) {
        this.tokens = tokens;
        this.reporter = reporter;
    }

    public Program parseProgram() {
        Token start = peek();
        consume(TokenType.SHURU, "A program must start with 'শুরু'");
        List<Stmt> statements = new ArrayList<>();
        while (!check(TokenType.SHESH) && !isAtEnd()) {
            Stmt stmt = declaration();
            if (stmt != null) {
                statements.add(stmt);
            }
        }
        consume(TokenType.SHESH, "A program must end with 'শেষ'");
        return new Program(statements, start.line, start.column);
    }

    // ---- Statements ----

    private Stmt declaration() {
        try {
            return statement();
        } catch (ParseError e) {
            synchronize();
            return null;
        }
    }

    private Stmt statement() {
        if (check(TokenType.PURNO) || check(TokenType.DOSHOMIK)) {
            return varDeclStatement();
        }
        if (check(TokenType.JODI)) {
            return ifStatement();
        }
        if (check(TokenType.JOTOKHON)) {
            return whileStatement();
        }
        if (check(TokenType.DEKHAO)) {
            return printStatement();
        }
        if (check(TokenType.IDENTIFIER)) {
            return assignStatement();
        }
        Token bad = peek();
        error(bad, "Expected a statement (a declaration, assignment, 'যদি', or 'দেখাও') but found '" + bad.lexeme + "'");
        throw new ParseError();
    }

    private Stmt varDeclStatement() {
        Token typeToken = advance();
        VarType varType = typeToken.type == TokenType.PURNO ? VarType.PURNO : VarType.DOSHOMIK;
        Token nameToken = consume(TokenType.IDENTIFIER, "Expected a variable name after the type");
        Expr initializer = null;
        if (match(TokenType.ASSIGN)) {
            initializer = expression();
        }
        consume(TokenType.SEMI, "Expected ';' after the variable declaration");
        return new VarDeclStmt(varType, nameToken.lexeme, initializer, typeToken.line, typeToken.column);
    }

    private Stmt assignStatement() {
        Token nameToken = advance();
        consume(TokenType.ASSIGN, "Expected '=' after the identifier '" + nameToken.lexeme + "'");
        Expr value = expression();
        consume(TokenType.SEMI, "Expected ';' after the assignment");
        return new AssignStmt(nameToken.lexeme, value, nameToken.line, nameToken.column);
    }

    private Stmt printStatement() {
        Token keyword = advance();
        consume(TokenType.LPAREN, "Expected '(' after 'দেখাও'");
        Expr value = expression();
        consume(TokenType.RPAREN, "Expected ')' after the দেখাও expression");
        consume(TokenType.SEMI, "Expected ';' after the দেখাও statement");
        return new PrintStmt(value, keyword.line, keyword.column);
    }

    private Stmt ifStatement() {
        Token keyword = advance();
        consume(TokenType.LPAREN, "Expected '(' after 'যদি'");
        Expr condition = expression();
        consume(TokenType.RPAREN, "Expected ')' after the যদি condition");
        BlockStmt thenBranch = block();
        BlockStmt elseBranch = null;
        if (match(TokenType.NAHOLE)) {
            elseBranch = block();
        }
        return new IfStmt(condition, thenBranch, elseBranch, keyword.line, keyword.column);
    }

    private Stmt whileStatement() {
        Token keyword = advance();
        consume(TokenType.LPAREN, "Expected '(' after 'যতক্ষণ'");
        Expr condition = expression();
        consume(TokenType.RPAREN, "Expected ')' after the যতক্ষণ condition");
        BlockStmt body = block();
        return new WhileStmt(condition, body, keyword.line, keyword.column);
    }

    private BlockStmt block() {
        Token start = consume(TokenType.SHURU, "Expected 'শুরু' to start a block");
        List<Stmt> statements = new ArrayList<>();
        while (!check(TokenType.SHESH) && !isAtEnd()) {
            Stmt stmt = declaration();
            if (stmt != null) {
                statements.add(stmt);
            }
        }
        consume(TokenType.SHESH, "Expected 'শেষ' to close the block");
        return new BlockStmt(statements, start.line, start.column);
    }

    // ---- Expressions: precedence climbing, lowest to highest ----
    // expression -> logicalOr -> logicalAnd -> equality -> relational -> additive -> multiplicative -> unary -> primary

    private Expr expression() {
        return logicalOr();
    }

    private Expr logicalOr() {
        Expr left = logicalAnd();
        while (check(TokenType.OR)) {
            Token op = advance();
            Expr right = logicalAnd();
            left = new BinaryExpr(left, BinaryOp.OR, right, op.line, op.column);
        }
        return left;
    }

    private Expr logicalAnd() {
        Expr left = equality();
        while (check(TokenType.AND)) {
            Token op = advance();
            Expr right = equality();
            left = new BinaryExpr(left, BinaryOp.AND, right, op.line, op.column);
        }
        return left;
    }

    private Expr equality() {
        Expr left = relational();
        while (check(TokenType.EQ) || check(TokenType.NEQ)) {
            Token op = advance();
            Expr right = relational();
            BinaryOp binOp = op.type == TokenType.EQ ? BinaryOp.EQ : BinaryOp.NEQ;
            left = new BinaryExpr(left, binOp, right, op.line, op.column);
        }
        return left;
    }

    private Expr relational() {
        Expr left = additive();
        while (check(TokenType.LT) || check(TokenType.GT) || check(TokenType.LE) || check(TokenType.GE)) {
            Token op = advance();
            BinaryOp binOp = switch (op.type) {
                case LT -> BinaryOp.LT;
                case GT -> BinaryOp.GT;
                case LE -> BinaryOp.LE;
                case GE -> BinaryOp.GE;
                default -> throw new IllegalStateException("unreachable");
            };
            Expr right = additive();
            left = new BinaryExpr(left, binOp, right, op.line, op.column);
        }
        return left;
    }

    private Expr additive() {
        Expr left = multiplicative();
        while (check(TokenType.PLUS) || check(TokenType.MINUS)) {
            Token op = advance();
            BinaryOp binOp = op.type == TokenType.PLUS ? BinaryOp.ADD : BinaryOp.SUB;
            Expr right = multiplicative();
            left = new BinaryExpr(left, binOp, right, op.line, op.column);
        }
        return left;
    }

    private Expr multiplicative() {
        Expr left = unary();
        while (check(TokenType.STAR) || check(TokenType.SLASH) || check(TokenType.PERCENT)) {
            Token op = advance();
            BinaryOp binOp = switch (op.type) {
                case STAR -> BinaryOp.MUL;
                case SLASH -> BinaryOp.DIV;
                case PERCENT -> BinaryOp.MOD;
                default -> throw new IllegalStateException("unreachable");
            };
            Expr right = unary();
            left = new BinaryExpr(left, binOp, right, op.line, op.column);
        }
        return left;
    }

    private Expr unary() {
        if (check(TokenType.NOT) || check(TokenType.MINUS) || check(TokenType.PLUS)) {
            Token op = advance();
            UnaryOp unaryOp = switch (op.type) {
                case NOT -> UnaryOp.NOT;
                case MINUS -> UnaryOp.NEG;
                case PLUS -> UnaryOp.POS;
                default -> throw new IllegalStateException("unreachable");
            };
            Expr operand = unary();
            return new UnaryExpr(unaryOp, operand, op.line, op.column);
        }
        return primary();
    }

    private Expr primary() {
        Token token = peek();
        if (token.type == TokenType.INT_LITERAL) {
            advance();
            return NumberLiteral.ofInt(parseIntLiteral(token.lexeme), token.lexeme, token.line, token.column);
        }
        if (token.type == TokenType.FLOAT_LITERAL) {
            advance();
            return NumberLiteral.ofFloat(parseFloatLiteral(token.lexeme), token.lexeme, token.line, token.column);
        }
        if (token.type == TokenType.IDENTIFIER) {
            advance();
            return new IdentifierExpr(token.lexeme, token.line, token.column);
        }
        if (match(TokenType.LPAREN)) {
            Expr inner = expression();
            consume(TokenType.RPAREN, "Expected ')' after the expression");
            return inner;
        }
        error(token, "Expected an expression but found '" + token.lexeme + "'");
        throw new ParseError();
    }

    private long parseIntLiteral(String lexeme) {
        long value = 0;
        for (int i = 0; i < lexeme.length(); i++) {
            value = value * 10 + digitValue(lexeme.charAt(i));
        }
        return value;
    }

    private double parseFloatLiteral(String lexeme) {
        int dot = lexeme.indexOf('.');
        String wholePart = lexeme.substring(0, dot);
        String fracPart = lexeme.substring(dot + 1);
        double value = 0;
        for (int i = 0; i < wholePart.length(); i++) {
            value = value * 10 + digitValue(wholePart.charAt(i));
        }
        double frac = 0;
        double scale = 0.1;
        for (int i = 0; i < fracPart.length(); i++) {
            frac += digitValue(fracPart.charAt(i)) * scale;
            scale *= 0.1;
        }
        return value + frac;
    }

    private int digitValue(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        return c - '০'; // Bengali digit block starts at ০ (U+09E6); lexer guarantees only these two ranges reach here
    }

    // ---- Token stream helpers ----

    private boolean match(TokenType type) {
        if (check(type)) {
            advance();
            return true;
        }
        return false;
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) {
            return type == TokenType.EOF;
        }
        return peek().type == type;
    }

    private Token advance() {
        if (!isAtEnd()) {
            current++;
        }
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type == TokenType.EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) {
            return advance();
        }
        Token bad = peek();
        error(bad, message + " (found '" + bad.lexeme + "')");
        throw new ParseError();
    }

    private void error(Token token, String message) {
        reporter.report(Phase.SYNTAX, token.line, token.column, message);
    }

    // Basic syntax error recovery: skip tokens until we pass a ';' or reach a
    // token that plausibly starts the next statement, then resume parsing there.
    private void synchronize() {
        while (!isAtEnd()) {
            if (previous().type == TokenType.SEMI) {
                return;
            }
            boolean atStatementStart = switch (peek().type) {
                case SHESH, JODI, JOTOKHON, PURNO, DOSHOMIK, DEKHAO -> true;
                default -> false;
            };
            if (atStatementStart) {
                return;
            }
            advance();
        }
    }
}
