package ankur.tests;

import ankur.codegen.PythonCodeGenerator;
import ankur.errors.ErrorReporter;
import ankur.lexer.Lexer;
import ankur.lexer.Token;
import ankur.parser.Parser;
import ankur.parser.ast.Program;
import ankur.semantic.SemanticAnalyzer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static ankur.tests.TestRunner.assertEquals;
import static ankur.tests.TestRunner.assertTrue;
import static ankur.tests.TestRunner.check;
import static ankur.tests.TestRunner.skip;

// Runs the generated Python for real, the same way the Java tests really compile and the
// WebAssembly tests really execute. Python is not part of the JDK, so when the interpreter is
// missing these report SKIP rather than passing quietly.
public final class PythonCodeGeneratorTest {

    private static final String INTERPRETER = findInterpreter();

    public static void runAll() {
        if (INTERPRETER == null) {
            skip("generated Python runs and prints", "no python interpreter found on PATH");
            return;
        }
        check("arithmetic respects operator precedence at runtime",
                () -> assertRuns("শুরু দেখাও(২ + ৩ * ৪); শেষ", "১৪"));
        // Python's `/` always produces a float, so পূর্ণ division has to come out as `//`:
        // without that this prints ৩.৫ instead of ৩.
        check("পূর্ণ division truncates the way Ankur means it to",
                () -> assertRuns("শুরু দেখাও(৭ / ২); শেষ", "৩"));
        check("দশমিক division keeps its fraction",
                () -> assertRuns("শুরু দেখাও(৭.০ / ২.০); শেষ", "৩.৫"));
        check("a যতক্ষণ loop runs to completion",
                () -> assertRuns("শুরু পূর্ণ ম = ০; পূর্ণ গ = ১; যতক্ষণ (গ <= ১০) শুরু ম = ম + গ; গ = গ + ১; শেষ দেখাও(ম); শেষ",
                        "৫৫"));
        check("যদি / নাহলে picks the right branch",
                () -> assertRuns("শুরু পূর্ণ ব = ২০; যদি (ব >= ১৮) শুরু দেখাও(১); শেষ নাহলে শুরু দেখাও(০); শেষ শেষ",
                        "১"));
        // Python has no block scope at all, so without per-declaration renaming the inner ক
        // would overwrite the outer one and the second line would print ২.
        check("a shadowed variable keeps the two values apart",
                () -> assertRuns("শুরু পূর্ণ ক = ১; যদি (ক > ০) শুরু পূর্ণ ক = ২; দেখাও(ক); শেষ দেখাও(ক); শেষ",
                        "২", "১"));
        check("a বাক্য prints as written", () -> assertRuns("শুরু বাক্য ন = \"মাহিদ\"; দেখাও(ন); শেষ", "মাহিদ"));
        check("&& and || become and / or",
                () -> assertRuns("শুরু পূর্ণ ক = ৫; যদি (ক > ০ && ক < ১০) শুরু দেখাও(১); শেষ শেষ", "১"));
    }

    private static void assertRuns(String source, String... expectedLines) throws IOException, InterruptedException {
        ErrorReporter reporter = new ErrorReporter();
        List<Token> tokens = new Lexer(source, reporter).scanTokens();
        Program program = new Parser(tokens, reporter).parseProgram();
        assertTrue(!reporter.hasErrors(), "expected no lexical/syntax errors: " + reporter.errors());
        SemanticAnalyzer analyzer = new SemanticAnalyzer(reporter);
        analyzer.analyze(program);
        assertTrue(!reporter.hasErrors(), "expected no semantic errors: " + reporter.errors());

        String pythonSource = new PythonCodeGenerator(analyzer.expressionTypes()).generate(program);
        Path dir = Files.createTempDirectory("ankur-python-test");
        Path file = dir.resolve("program.py");
        Files.writeString(file, pythonSource, StandardCharsets.UTF_8);

        ProcessBuilder builder = new ProcessBuilder(INTERPRETER, file.toString());
        builder.redirectErrorStream(true);
        // Without this the interpreter encodes its output with the Windows console codepage,
        // which cannot represent Bangla script.
        builder.environment().put("PYTHONIOENCODING", "utf-8");
        Process process = builder.start();
        String output;
        try (var stream = process.getInputStream()) {
            output = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(process.waitFor(30, TimeUnit.SECONDS), "python should finish within 30 seconds");
        assertEquals(0, process.exitValue(), "python should run the program without error:\n"
                + output + "\n--- generated ---\n" + pythonSource);

        List<String> actual = output.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        assertEquals(List.of(expectedLines), actual, "printed output");
    }

    // "python" on Windows, "python3" on most other systems; returns null when neither works.
    private static String findInterpreter() {
        for (String candidate : new String[]{"python", "python3"}) {
            try {
                Process process = new ProcessBuilder(candidate, "--version").redirectErrorStream(true).start();
                if (process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0) {
                    return candidate;
                }
            } catch (IOException | InterruptedException ignored) {
                // Try the next name.
            }
        }
        return null;
    }
}
