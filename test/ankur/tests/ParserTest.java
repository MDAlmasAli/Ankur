package ankur.tests;

import ankur.errors.ErrorReporter;
import ankur.lexer.Lexer;
import ankur.lexer.Token;
import ankur.parser.Parser;
import ankur.parser.ast.BinaryExpr;
import ankur.parser.ast.BinaryOp;
import ankur.parser.ast.IfStmt;
import ankur.parser.ast.NumberLiteral;
import ankur.parser.ast.Program;
import ankur.parser.ast.VarDeclStmt;
import ankur.parser.ast.VarType;
import ankur.parser.ast.WhileStmt;

import java.util.List;

import static ankur.tests.TestRunner.assertEquals;
import static ankur.tests.TestRunner.assertTrue;
import static ankur.tests.TestRunner.check;

public final class ParserTest {

    public static void runAll() {
        check("empty program parses with no errors", ParserTest::emptyProgram);
        check("variable declaration with initializer", ParserTest::varDeclWithInitializer);
        check("if-else with a comparison condition", ParserTest::ifElse);
        check("while loop with a comparison condition", ParserTest::whileLoop);
        check("multiplication binds tighter than addition", ParserTest::precedence);
        check("missing semicolon recovers at next statement keyword", ParserTest::recoverAtKeyword);
        check("missing expression recovers by skipping to semicolon", ParserTest::recoverAtSemicolon);
    }

    private static Program parse(String source, ErrorReporter reporter) {
        List<Token> tokens = new Lexer(source, reporter).scanTokens();
        return new Parser(tokens, reporter).parseProgram();
    }

    private static void emptyProgram() {
        ErrorReporter reporter = new ErrorReporter();
        Program program = parse("শুরু শেষ", reporter);
        assertTrue(!reporter.hasErrors(), "no errors expected");
        assertEquals(0, program.statements().size(), "statement count");
    }

    private static void varDeclWithInitializer() {
        ErrorReporter reporter = new ErrorReporter();
        Program program = parse("শুরু পূর্ণ x = 5; শেষ", reporter);
        assertTrue(!reporter.hasErrors(), "no errors expected");
        assertEquals(1, program.statements().size(), "statement count");
        VarDeclStmt decl = (VarDeclStmt) program.statements().get(0);
        assertEquals("x", decl.name(), "variable name");
        assertEquals(VarType.PURNO, decl.type(), "variable type");
        assertTrue(decl.initializer() instanceof NumberLiteral, "initializer should be a number literal");
    }

    private static void ifElse() {
        ErrorReporter reporter = new ErrorReporter();
        String src = "শুরু পূর্ণ x = 5; যদি (x > 3) শুরু দেখাও(x); শেষ নাহলে শুরু x = 0; শেষ শেষ";
        Program program = parse(src, reporter);
        assertTrue(!reporter.hasErrors(), "no errors expected: " + reporter.errors());
        assertEquals(2, program.statements().size(), "top-level statement count");
        IfStmt ifStmt = (IfStmt) program.statements().get(1);
        assertTrue(ifStmt.condition() instanceof BinaryExpr, "condition should be a comparison");
        assertEquals(BinaryOp.GT, ((BinaryExpr) ifStmt.condition()).op(), "comparison operator");
        assertEquals(1, ifStmt.thenBranch().statements().size(), "then-branch statement count");
        assertTrue(ifStmt.elseBranch() != null, "else-branch should be present");
        assertEquals(1, ifStmt.elseBranch().statements().size(), "else-branch statement count");
    }

    private static void whileLoop() {
        ErrorReporter reporter = new ErrorReporter();
        String src = "শুরু পূর্ণ x = 0; যতক্ষণ (x < 10) শুরু x = x + 1; শেষ শেষ";
        Program program = parse(src, reporter);
        assertTrue(!reporter.hasErrors(), "no errors expected: " + reporter.errors());
        assertEquals(2, program.statements().size(), "top-level statement count");
        WhileStmt whileStmt = (WhileStmt) program.statements().get(1);
        assertTrue(whileStmt.condition() instanceof BinaryExpr, "condition should be a comparison");
        assertEquals(BinaryOp.LT, ((BinaryExpr) whileStmt.condition()).op(), "comparison operator");
        assertEquals(1, whileStmt.body().statements().size(), "body statement count");
    }

    private static void precedence() {
        ErrorReporter reporter = new ErrorReporter();
        Program program = parse("শুরু পূর্ণ x = 1 + 2 * 3; শেষ", reporter);
        assertTrue(!reporter.hasErrors(), "no errors expected");
        VarDeclStmt decl = (VarDeclStmt) program.statements().get(0);
        BinaryExpr top = (BinaryExpr) decl.initializer();
        assertEquals(BinaryOp.ADD, top.op(), "outermost operator should be +");
        assertTrue(top.left() instanceof NumberLiteral, "left of + should be the literal 1");
        assertTrue(top.right() instanceof BinaryExpr, "right of + should be the nested 2 * 3");
        assertEquals(BinaryOp.MUL, ((BinaryExpr) top.right()).op(), "nested operator should be *");
    }

    private static void recoverAtKeyword() {
        ErrorReporter reporter = new ErrorReporter();
        Program program = parse("শুরু পূর্ণ x = 5 পূর্ণ y = 6; শেষ", reporter);
        assertEquals(1, reporter.errors().size(), "expected exactly one syntax error");
        assertEquals(1, program.statements().size(), "only the recovered statement should remain");
        VarDeclStmt decl = (VarDeclStmt) program.statements().get(0);
        assertEquals("y", decl.name(), "surviving statement should be y's declaration");
    }

    private static void recoverAtSemicolon() {
        ErrorReporter reporter = new ErrorReporter();
        Program program = parse("শুরু পূর্ণ x = ; পূর্ণ y = 6; শেষ", reporter);
        assertEquals(1, reporter.errors().size(), "expected exactly one syntax error");
        assertEquals(1, program.statements().size(), "only the recovered statement should remain");
        VarDeclStmt decl = (VarDeclStmt) program.statements().get(0);
        assertEquals("y", decl.name(), "surviving statement should be y's declaration");
    }
}
