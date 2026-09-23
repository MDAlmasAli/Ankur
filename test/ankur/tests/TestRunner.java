package ankur.tests;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

// Tiny dependency-free test harness (no Maven/JUnit on this machine). Run via test.ps1.
public final class TestRunner {

    @FunctionalInterface
    public interface TestCase {
        void run() throws Exception;
    }

    private static int passed = 0;
    private static int failed = 0;
    private static int skipped = 0;

    // Reports a test that could not run because the machine lacks something it needs (the
    // WebAssembly tests need a WebAssembly host). Reported separately from a pass, so a
    // missing tool can never be mistaken for a working feature.
    public static void skip(String name, String reason) {
        skipped++;
        System.out.println("  SKIP  " + name + " -> " + reason);
    }

    public static void check(String name, TestCase testCase) {
        try {
            testCase.run();
            passed++;
            System.out.println("  PASS  " + name);
        } catch (AssertionError | Exception e) {
            failed++;
            System.out.println("  FAIL  " + name + " -> " + e.getMessage());
        }
    }

    public static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(message + " (expected <" + expected + "> but was <" + actual + ">)");
        }
    }

    public static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        // See ankur.Main: Windows JVMs default stdout to a codepage that can't render Bangla.
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));

        System.out.println("Lexer");
        LexerTest.runAll();
        System.out.println("Parser");
        ParserTest.runAll();
        System.out.println("Semantic Analyzer");
        SemanticAnalyzerTest.runAll();
        System.out.println("Three Address Code");
        TacGeneratorTest.runAll();
        System.out.println("Code Generator (Java)");
        JavaCodeGeneratorTest.runAll();
        System.out.println("Code Generator (Python)");
        PythonCodeGeneratorTest.runAll();
        System.out.println("Code Generator (WebAssembly)");
        WasmCodeGeneratorTest.runAll();

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed"
                + (skipped > 0 ? ", " + skipped + " skipped" : ""));
        if (failed > 0) {
            System.exit(1);
        }
    }
}
