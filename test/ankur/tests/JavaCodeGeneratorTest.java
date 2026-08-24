package ankur.tests;

import ankur.codegen.JavaCodeGenerator;
import ankur.errors.ErrorReporter;
import ankur.lexer.Lexer;
import ankur.lexer.Token;
import ankur.parser.Parser;
import ankur.parser.ast.Program;
import ankur.semantic.SemanticAnalyzer;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static ankur.tests.TestRunner.assertTrue;
import static ankur.tests.TestRunner.check;

// Compiles the generated Java with the JDK's own in-process compiler (no external javac
// process, no dependency) so these tests prove "generates a valid, executable target file"
// for real, not just that the generator produced plausible-looking text.
public final class JavaCodeGeneratorTest {

    public static void runAll() {
        check("generated Java compiles for arithmetic + print", JavaCodeGeneratorTest::compilesArithmetic);
        check("generated Java compiles for if-else", JavaCodeGeneratorTest::compilesIfElse);
        check("generated Java compiles for a while loop", JavaCodeGeneratorTest::compilesWhileLoop);
        check("shadowed variables still produce compilable Java", JavaCodeGeneratorTest::shadowingCompiles);
    }

    private static Program compileToValidatedAst(String source) {
        ErrorReporter reporter = new ErrorReporter();
        List<Token> tokens = new Lexer(source, reporter).scanTokens();
        Program program = new Parser(tokens, reporter).parseProgram();
        assertTrue(!reporter.hasErrors(), "expected no lexical/syntax errors: " + reporter.errors());
        new SemanticAnalyzer(reporter).analyze(program);
        assertTrue(!reporter.hasErrors(), "expected no semantic errors: " + reporter.errors());
        return program;
    }

    private static void assertCompiles(String source, String className) throws IOException {
        Program program = compileToValidatedAst(source);
        String javaSource = new JavaCodeGenerator().generate(program, className);

        Path tempDir = Files.createTempDirectory("ankur-codegen-test");
        Path sourceFile = tempDir.resolve(className + ".java");
        Files.writeString(sourceFile, javaSource, StandardCharsets.UTF_8);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int result = compiler.run(null, null, null, "-d", tempDir.toString(), sourceFile.toString());
        assertTrue(result == 0, "generated Java should compile cleanly:\n" + javaSource);
    }

    private static void compilesArithmetic() throws IOException {
        assertCompiles("শুরু পূর্ণ x = 5; পূর্ণ y = 10; দেখাও(x + y * 2); শেষ", "ArithmeticCodegenTest");
    }

    private static void compilesIfElse() throws IOException {
        assertCompiles(
                "শুরু পূর্ণ x = 5; যদি (x > 3) শুরু দেখাও(1); শেষ নাহলে শুরু দেখাও(0); শেষ শেষ",
                "IfElseCodegenTest");
    }

    private static void compilesWhileLoop() throws IOException {
        assertCompiles(
                "শুরু পূর্ণ x = 0; যতক্ষণ (x < 5) শুরু x = x + 1; শেষ দেখাও(x); শেষ",
                "WhileCodegenTest");
    }

    // The critical case: Ankur allows a nested block to shadow an outer variable name, but
    // Java rejects redeclaring a name still in scope. If JavaCodeGenerator's per-declaration
    // unique naming (see its scopes field) is broken, this is the test that catches it --
    // as a real javac compile failure, not a guess.
    private static void shadowingCompiles() throws IOException {
        assertCompiles(
                "শুরু পূর্ণ x = 1; যদি (x > 0) শুরু পূর্ণ x = 2; দেখাও(x); শেষ দেখাও(x); শেষ",
                "ShadowCodegenTest");
    }
}
