package ankur.tests;

import ankur.codegen.NodeRunnerGenerator;
import ankur.codegen.WasmCodeGenerator;
import ankur.codegen.wasm.WasmBinaryWriter;
import ankur.codegen.wasm.WasmModule;
import ankur.codegen.wasm.WatWriter;
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

// Runs the generated WebAssembly for real: the module is encoded to binary, handed to Node's
// WebAssembly host together with the generated .mjs runner, and the program's actual printed
// output is compared against what the same Ankur program means. That is the only way to prove
// the binary encoder is correct -- a malformed module is rejected by the host, and a
// mis-encoded instruction shows up as a wrong number.
//
// Node is the one thing here that is not part of the JDK, so when it is missing these tests
// report SKIP rather than passing quietly.
public final class WasmCodeGeneratorTest {

    private static final Boolean NODE_AVAILABLE = detectNode();

    public static void runAll() {
        // These two need nothing but the JDK, so they always run.
        check("generated .wasm starts with the WebAssembly magic number", WasmCodeGeneratorTest::binaryHeaderIsValid);
        check("generated .wat mirrors the instructions in the binary", WasmCodeGeneratorTest::watMatchesProgram);

        if (!NODE_AVAILABLE) {
            skip("generated WebAssembly runs and prints", "node not found on PATH");
            return;
        }
        check("arithmetic respects operator precedence at runtime",
                () -> assertRuns("শুরু দেখাও(২ + ৩ * ৪); শেষ", "১৪"));
        check("a যতক্ষণ loop runs to completion",
                () -> assertRuns("শুরু পূর্ণ ম = ০; পূর্ণ গ = ১; যতক্ষণ (গ <= ১০) শুরু ম = ম + গ; গ = গ + ১; শেষ দেখাও(ম); শেষ",
                        "৫৫"));
        check("যদি / নাহলে picks the right branch",
                () -> assertRuns("শুরু পূর্ণ ব = ২০; যদি (ব >= ১৮) শুরু দেখাও(১); শেষ নাহলে শুরু দেখাও(০); শেষ শেষ",
                        "১"));
        // WebAssembly has no implicit conversion, so this fails outright unless the generator
        // inserts f64.convert_i32_s for the পূর্ণ operand.
        check("পূর্ণ widens to দশমিক in a mixed expression",
                () -> assertRuns("শুরু দশমিক দ = ২.৫; পূর্ণ প = ২; দেখাও(দ * প); শেষ", "৫.০"));
        check("a shadowed variable keeps the two values apart",
                () -> assertRuns("শুরু পূর্ণ ক = ১; যদি (ক > ০) শুরু পূর্ণ ক = ২; দেখাও(ক); শেষ দেখাও(ক); শেষ",
                        "২", "১"));
        // The initializer of a shadowing declaration reads the OUTER variable, so this prints
        // ২ and not ১: the new local must not exist yet while its own initializer is generated.
        check("a shadowing declaration reads the outer variable in its initializer",
                () -> assertRuns("শুরু পূর্ণ ক = ১; যদি (ক > ০) শুরু পূর্ণ ক = ক + ১; দেখাও(ক); শেষ শেষ", "২"));
        // An eagerly evaluated right-hand side would trap here on an integer division by
        // zero, so this is what proves && really short-circuits in the generated module.
        check("&& short-circuits instead of trapping",
                () -> assertRuns("শুরু পূর্ণ ক = ০; যদি (ক != ০ && ১০ / ক > ১) শুরু দেখাও(১); শেষ নাহলে শুরু দেখাও(০); শেষ শেষ",
                        "০"));
        check("দশমিক % works despite having no f64 remainder instruction",
                () -> assertRuns("শুরু দশমিক ক = ৭.৫; দেখাও(ক % ২.০); শেষ", "১.৫"));
        check("a declaration inside a loop is re-initialized each iteration",
                () -> assertRuns("শুরু পূর্ণ গ = ০; যতক্ষণ (গ < ৩) শুরু পূর্ণ ভ = ৭; দেখাও(ভ); গ = গ + ১; শেষ শেষ",
                        "৭", "৭", "৭"));
        // WebAssembly has no string type: these prove the literal really made it into the
        // module's memory and that the (pointer, length) pair survives being stored in a
        // variable and read back.
        check("a বাক্য literal prints from linear memory",
                () -> assertRuns("শুরু দেখাও(\"মাহিদ\"); শেষ", "মাহিদ"));
        check("a বাক্য variable keeps its pointer and length",
                () -> assertRuns("শুরু বাক্য ন = \"অঙ্কুর\"; দেখাও(ন); শেষ", "অঙ্কুর"));
        check("two বাক্য values do not run into each other in memory",
                () -> assertRuns("শুরু বাক্য ক = \"এক\"; বাক্য খ = \"দুই\"; দেখাও(ক); দেখাও(খ); শেষ",
                        "এক", "দুই"));
        // A বাক্য's digits are the author's own text, so they stay ASCII rather than being
        // rewritten into Bangla-Indic the way a printed number is.
        check("digits inside a বাক্য are left exactly as written",
                () -> assertRuns("শুরু দেখাও(\"room 101\"); শেষ", "room 101"));
    }

    // ---- Tests that need no host ----

    private static void binaryHeaderIsValid() {
        byte[] binary = WasmBinaryWriter.write(moduleFor("শুরু দেখাও(১); শেষ", "HeaderTest"));
        assertTrue(binary.length > 8, "module should have content beyond its header");
        assertEquals(0x00, binary[0] & 0xFF, "byte 0 of the magic number");
        assertEquals(0x61, binary[1] & 0xFF, "byte 1 of the magic number ('a')");
        assertEquals(0x73, binary[2] & 0xFF, "byte 2 of the magic number ('s')");
        assertEquals(0x6D, binary[3] & 0xFF, "byte 3 of the magic number ('m')");
        assertEquals(0x01, binary[4] & 0xFF, "binary format version");
    }

    private static void watMatchesProgram() {
        String wat = WatWriter.write(moduleFor("শুরু পূর্ণ ক = ১; দেখাও(ক + ২); শেষ", "WatTest"));
        assertTrue(wat.contains("(local i32)"), "the declaration should hoist an i32 local:\n" + wat);
        assertTrue(wat.contains("i32.add"), "the addition should be emitted:\n" + wat);
        assertTrue(wat.contains("call " + WasmModule.PRINT_INT_INDEX),
                "দেখাও should call the imported integer printer:\n" + wat);
        assertTrue(wat.contains(WasmModule.ENTRY_POINT), "the entry point should be exported:\n" + wat);
    }

    // ---- Running the module for real ----

    private static void assertRuns(String source, String... expectedLines) throws IOException, InterruptedException {
        String name = "WasmRunTest";
        WasmModule module = moduleFor(source, name);

        Path dir = Files.createTempDirectory("ankur-wasm-test");
        Files.write(dir.resolve(name + ".wasm"), WasmBinaryWriter.write(module));
        Files.writeString(dir.resolve(name + ".mjs"), NodeRunnerGenerator.generate(name), StandardCharsets.UTF_8);

        ProcessBuilder builder = new ProcessBuilder("node", name + ".mjs");
        builder.directory(dir.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output;
        try (var stream = process.getInputStream()) {
            output = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(process.waitFor(30, TimeUnit.SECONDS), "node should finish within 30 seconds");
        assertEquals(0, process.exitValue(), "node should run the module without error:\n" + output);

        List<String> actual = output.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        assertEquals(List.of(expectedLines), actual, "printed output");
    }

    private static WasmModule moduleFor(String source, String name) {
        ErrorReporter reporter = new ErrorReporter();
        List<Token> tokens = new Lexer(source, reporter).scanTokens();
        Program program = new Parser(tokens, reporter).parseProgram();
        assertTrue(!reporter.hasErrors(), "expected no lexical/syntax errors: " + reporter.errors());
        SemanticAnalyzer analyzer = new SemanticAnalyzer(reporter);
        analyzer.analyze(program);
        assertTrue(!reporter.hasErrors(), "expected no semantic errors: " + reporter.errors());
        // The generator needs the analyzer's expression types, not just the AST: WebAssembly
        // has no implicit conversions, so codegen has to know what every expression is.
        return new WasmCodeGenerator(analyzer.expressionTypes()).generate(program, name);
    }

    private static boolean detectNode() {
        try {
            Process process = new ProcessBuilder("node", "--version").redirectErrorStream(true).start();
            return process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
}
