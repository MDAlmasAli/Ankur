package ankur.tests;

import ankur.errors.ErrorReporter;
import ankur.lexer.Lexer;
import ankur.lexer.Token;
import ankur.parser.Parser;
import ankur.parser.ast.Program;
import ankur.semantic.SemanticAnalyzer;

import java.util.List;

import static ankur.tests.TestRunner.assertEquals;
import static ankur.tests.TestRunner.assertTrue;
import static ankur.tests.TestRunner.check;

public final class SemanticAnalyzerTest {

    public static void runAll() {
        check("well-typed program has no semantic errors", SemanticAnalyzerTest::validProgram);
        check("using an undeclared variable is an error", SemanticAnalyzerTest::undeclaredVariable);
        check("redeclaring a variable in the same scope is an error", SemanticAnalyzerTest::duplicateDeclaration);
        check("shadowing a variable in a nested block is allowed", SemanticAnalyzerTest::shadowingAllowed);
        check("assigning a দশমিক value to a পূর্ণ variable is an error", SemanticAnalyzerTest::narrowingAssignmentRejected);
        check("assigning a পূর্ণ value to a দশমিক variable is allowed", SemanticAnalyzerTest::wideningAssignmentAllowed);
        check("a non-boolean if-condition is an error", SemanticAnalyzerTest::nonBooleanCondition);
        check("&& requires two boolean operands", SemanticAnalyzerTest::logicalOperatorNeedsBooleans);
        check("a well-typed while loop has no semantic errors", SemanticAnalyzerTest::validWhileLoop);
        check("a non-boolean while-condition is an error", SemanticAnalyzerTest::nonBooleanWhileCondition);
        check("an out-of-range integer literal is an error", SemanticAnalyzerTest::integerLiteralOutOfRange);
        check("using a declared-but-unassigned variable is an error", SemanticAnalyzerTest::useBeforeInitialization);
        check("a variable with an initializer is not use-before-init", SemanticAnalyzerTest::initializerCountsAsAssigned);
        check("dividing an int literal by literal 0 is an error", SemanticAnalyzerTest::intDivisionByLiteralZeroRejected);
        check("dividing by a float literal 0.0 is allowed", SemanticAnalyzerTest::floatDivisionByZeroAllowed);
    }

    private static ErrorReporter analyze(String source) {
        ErrorReporter reporter = new ErrorReporter();
        List<Token> tokens = new Lexer(source, reporter).scanTokens();
        Program program = new Parser(tokens, reporter).parseProgram();
        if (!reporter.hasErrors()) {
            new SemanticAnalyzer(reporter).analyze(program);
        }
        return reporter;
    }

    private static void validProgram() {
        ErrorReporter reporter = analyze(
                "শুরু "
                        + "পূর্ণ x = 5; "
                        + "দশমিক y = 3.5; "
                        + "যদি (x > 2 && y < 10.0) শুরু দেখাও(x); শেষ নাহলে শুরু দেখাও(y); শেষ "
                        + "শেষ");
        assertTrue(!reporter.hasErrors(), "expected no semantic errors, got: " + reporter.errors());
    }

    private static void undeclaredVariable() {
        ErrorReporter reporter = analyze("শুরু x = 5; শেষ");
        assertEquals(1, reporter.errors().size(), "expected exactly one semantic error");
    }

    private static void duplicateDeclaration() {
        ErrorReporter reporter = analyze("শুরু পূর্ণ x = 1; পূর্ণ x = 2; শেষ");
        assertEquals(1, reporter.errors().size(), "expected exactly one semantic error");
    }

    private static void shadowingAllowed() {
        ErrorReporter reporter = analyze("শুরু পূর্ণ x = 1; যদি (x > 0) শুরু পূর্ণ x = 2; দেখাও(x); শেষ শেষ");
        assertTrue(!reporter.hasErrors(), "shadowing in a nested block should be allowed, got: " + reporter.errors());
    }

    private static void narrowingAssignmentRejected() {
        ErrorReporter reporter = analyze("শুরু পূর্ণ x = 1; দশমিক y = 2.5; x = y; শেষ");
        assertEquals(1, reporter.errors().size(), "assigning দশমিক to পূর্ণ should be an error");
    }

    private static void wideningAssignmentAllowed() {
        ErrorReporter reporter = analyze("শুরু পূর্ণ x = 1; দশমিক y = 2.5; y = x; শেষ");
        assertTrue(!reporter.hasErrors(), "assigning পূর্ণ to দশমিক should be allowed, got: " + reporter.errors());
    }

    private static void nonBooleanCondition() {
        ErrorReporter reporter = analyze("শুরু পূর্ণ x = 1; যদি (x) শুরু দেখাও(x); শেষ শেষ");
        assertEquals(1, reporter.errors().size(), "a raw number as a condition should be a semantic error");
    }

    private static void logicalOperatorNeedsBooleans() {
        ErrorReporter reporter = analyze("শুরু পূর্ণ x = 1; পূর্ণ y = 2; যদি (x && y) শুরু দেখাও(x); শেষ শেষ");
        assertTrue(reporter.hasErrors(), "&& between two non-boolean operands should be an error");
    }

    private static void validWhileLoop() {
        ErrorReporter reporter = analyze("শুরু পূর্ণ x = 0; যতক্ষণ (x < 10) শুরু x = x + 1; শেষ শেষ");
        assertTrue(!reporter.hasErrors(), "expected no semantic errors, got: " + reporter.errors());
    }

    private static void nonBooleanWhileCondition() {
        ErrorReporter reporter = analyze("শুরু পূর্ণ x = 1; যতক্ষণ (x) শুরু x = x + 1; শেষ শেষ");
        assertEquals(1, reporter.errors().size(), "a raw number as a while-condition should be a semantic error");
    }

    private static void integerLiteralOutOfRange() {
        ErrorReporter reporter = analyze("শুরু পূর্ণ x = 99999999999; শেষ");
        assertTrue(reporter.hasErrors(), "an integer literal that doesn't fit in 32 bits should be an error");
    }

    private static void useBeforeInitialization() {
        ErrorReporter reporter = analyze("শুরু পূর্ণ x; দেখাও(x); শেষ");
        assertEquals(1, reporter.errors().size(), "reading an unassigned declared variable should be an error");
    }

    private static void initializerCountsAsAssigned() {
        ErrorReporter reporter = analyze("শুরু পূর্ণ x = 5; দেখাও(x); শেষ");
        assertTrue(!reporter.hasErrors(), "a variable declared with an initializer should not be use-before-init");
    }

    private static void intDivisionByLiteralZeroRejected() {
        ErrorReporter reporter = analyze("শুরু পূর্ণ x = 5 / 0; শেষ");
        assertEquals(1, reporter.errors().size(), "int division by literal 0 should be a semantic error");
    }

    private static void floatDivisionByZeroAllowed() {
        ErrorReporter reporter = analyze("শুরু দশমিক x = 5.0 / 0.0; শেষ");
        assertTrue(!reporter.hasErrors(), "float division by 0.0 is well-defined (Infinity), not an error");
    }
}
