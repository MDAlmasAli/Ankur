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
        System.out.println("Code Generator");
        JavaCodeGeneratorTest.runAll();

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }
}
