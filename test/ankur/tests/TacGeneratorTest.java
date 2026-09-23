package ankur.tests;

import ankur.errors.ErrorReporter;
import ankur.lexer.Lexer;
import ankur.lexer.Token;
import ankur.parser.Parser;
import ankur.parser.ast.Program;
import ankur.semantic.SemanticAnalyzer;
import ankur.tac.TacGenerator;
import ankur.tac.TacInstr;

import java.util.List;

import static ankur.tests.TestRunner.assertEquals;
import static ankur.tests.TestRunner.assertTrue;
import static ankur.tests.TestRunner.check;

// Checks the shape of the intermediate representation: that expressions really are flattened
// into one operation per instruction, and that যদি and যতক্ষণ really do become labels and
// jumps. These are the two properties that make it three-address code rather than a reprint
// of the tree.
public final class TacGeneratorTest {

    public static void runAll() {
        check("a nested expression is flattened into one operation per temporary",
                TacGeneratorTest::flattensNestedExpressions);
        check("যদি / নাহলে becomes a conditional jump, a jump, and two labels",
                TacGeneratorTest::ifElseBecomesJumps);
        check("যতক্ষণ re-tests its condition at the top of the loop",
                TacGeneratorTest::whileRetestsAtTheTop);
        check("a যদি with no নাহলে needs only one label", TacGeneratorTest::ifWithoutElse);
        check("a shadowed variable gets a distinct TAC name", TacGeneratorTest::shadowingIsDisambiguated);
    }

    private static void flattensNestedExpressions() {
        // ২ + ৩ * ৪ has two operations, so it needs two temporaries, and the multiplication
        // must be computed first for the precedence to have survived the lowering.
        List<TacInstr> tac = tacFor("শুরু দেখাও(২ + ৩ * ৪); শেষ");
        assertEquals(3, tac.size(), "instruction count");
        assertEquals("t1 = ৩ * ৪", tac.get(0).text(), "the multiplication comes first");
        assertEquals("t2 = ২ + t1", tac.get(1).text(), "the addition consumes its result");
        assertEquals("দেখাও t2", tac.get(2).text(), "the print uses the final temporary");
    }

    private static void ifElseBecomesJumps() {
        List<TacInstr> tac = tacFor("শুরু পূর্ণ ক = ১; যদি (ক > ০) শুরু দেখাও(১); শেষ নাহলে শুরু দেখাও(০); শেষ শেষ");
        assertEquals(1, count(tac, TacInstr.IfFalseGoto.class), "one conditional jump");
        assertEquals(1, count(tac, TacInstr.Goto.class), "one jump over the else branch");
        assertEquals(2, count(tac, TacInstr.Label.class), "one label per branch target");
    }

    private static void whileRetestsAtTheTop() {
        List<TacInstr> tac = tacFor("শুরু পূর্ণ ক = ০; যতক্ষণ (ক < ৩) শুরু ক = ক + ১; শেষ শেষ");
        // The loop's label must come before the comparison, or the condition would be tested
        // once and never again.
        int topLabel = indexOf(tac, TacInstr.Label.class);
        int comparison = indexOf(tac, TacInstr.BinOp.class);
        assertTrue(topLabel < comparison,
                "the loop label should precede the condition, but was at " + topLabel + " vs " + comparison);
        assertEquals(1, count(tac, TacInstr.IfFalseGoto.class), "one exit test");
        assertEquals(1, count(tac, TacInstr.Goto.class), "one jump back to the top");
    }

    private static void ifWithoutElse() {
        List<TacInstr> tac = tacFor("শুরু পূর্ণ ক = ১; যদি (ক > ০) শুরু দেখাও(১); শেষ শেষ");
        assertEquals(1, count(tac, TacInstr.Label.class), "only the target past the branch");
        assertEquals(0, count(tac, TacInstr.Goto.class), "nothing to jump over");
    }

    private static void shadowingIsDisambiguated() {
        // TAC operands are flat names, so the inner ক cannot reuse the outer one's.
        List<TacInstr> tac = tacFor("শুরু পূর্ণ ক = ১; যদি (ক > ০) শুরু পূর্ণ ক = ২; দেখাও(ক); শেষ শেষ");
        String text = tac.toString();
        assertTrue(text.contains("ক@2"), "the shadowing declaration should be renamed:\n" + text);
    }

    // ---- Helpers ----

    private static List<TacInstr> tacFor(String source) {
        ErrorReporter reporter = new ErrorReporter();
        List<Token> tokens = new Lexer(source, reporter).scanTokens();
        Program program = new Parser(tokens, reporter).parseProgram();
        assertTrue(!reporter.hasErrors(), "expected no lexical/syntax errors: " + reporter.errors());
        new SemanticAnalyzer(reporter).analyze(program);
        assertTrue(!reporter.hasErrors(), "expected no semantic errors: " + reporter.errors());
        return new TacGenerator().generate(program);
    }

    private static int count(List<TacInstr> tac, Class<? extends TacInstr> kind) {
        return (int) tac.stream().filter(kind::isInstance).count();
    }

    private static int indexOf(List<TacInstr> tac, Class<? extends TacInstr> kind) {
        for (int i = 0; i < tac.size(); i++) {
            if (kind.isInstance(tac.get(i))) {
                return i;
            }
        }
        return -1;
    }
}
