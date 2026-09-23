package ankur;

import ankur.codegen.JavaCodeGenerator;
import ankur.codegen.NodeRunnerGenerator;
import ankur.codegen.PythonCodeGenerator;
import ankur.codegen.WasmCodeGenerator;
import ankur.codegen.wasm.WasmBinaryWriter;
import ankur.codegen.wasm.WasmModule;
import ankur.codegen.wasm.WatWriter;
import ankur.errors.ErrorReporter;
import ankur.lexer.Lexer;
import ankur.lexer.Token;
import ankur.lexer.TokenType;
import ankur.parser.Parser;
import ankur.parser.ast.AstPrinter;
import ankur.parser.ast.Expr;
import ankur.parser.ast.Program;
import ankur.report.Console;
import ankur.report.Report;
import ankur.semantic.SemanticAnalyzer;
import ankur.semantic.Symbol;
import ankur.semantic.Type;
import ankur.tac.TacGenerator;
import ankur.tac.TacInstr;
import ankur.tac.TacPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// The whole compiler, as one callable unit: lexer -> parser -> semantic analyzer -> code
// generation, with the phase-by-phase report built up as a Report rather than printed.
//
// Returning the report instead of printing it is what lets the console front end (ankur.Main)
// and the playground GUI show exactly the same thing. They used to each walk the pipeline
// themselves, and the two outputs drifted apart the moment one of them changed.
public final class Compiler {

    // Which target code to emit. Either Java or Python satisfies course requirement 2.1;
    // WebAssembly is the optional target of 2.2. ALL emits every one of them, so a single run
    // shows the whole pipeline.
    public enum Target {
        JAVA, PYTHON, WASM, ALL;

        public boolean includesJava() {
            return this == JAVA || this == ALL;
        }

        public boolean includesPython() {
            return this == PYTHON || this == ALL;
        }

        public boolean includesWasm() {
            return this == WASM || this == ALL;
        }
    }

    // `report` is the phase-by-phase report, without the closing verdict block -- the caller
    // appends that with Console.verdict once it has added any phases of its own (the
    // playground adds one for actually running the program). The file fields are null for a
    // target that was not requested, or for a run that stopped before code generation.
    public record Result(
            boolean success,
            Report report,
            Path tacFile,
            Path javaFile,
            Path pythonFile,
            Path watFile,
            Path wasmFile,
            Path runnerFile) {
    }

    private Compiler() {
    }

    public static Result compile(String source, String name, Target target, Path outputDir) {
        Report report = new Report();
        ErrorReporter reporter = new ErrorReporter();

        printSource(report, source);

        // ---- Phase 1: lexical analysis ----
        Console.banner(report, 1, "LEXICAL ANALYSIS", "লেক্সিক্যাল অ্যানালাইসিস");
        List<Token> tokens = new Lexer(source, reporter).scanTokens();
        printTokens(report, tokens);
        if (reporter.hasErrors()) {
            return failure(report, reporter, "Lexical");
        }
        Console.status(report, "Lexical", true);
        report.append('\n');

        // ---- Phase 2: syntax analysis ----
        Console.banner(report, 2, "SYNTAX ANALYSIS", "সিনট্যাক্স অ্যানালাইসিস / AST");
        Program program = new Parser(tokens, reporter).parseProgram();
        // Print the tree even on failure: error recovery means parsing kept going past the bad
        // statement(s), and that recovered tree is worth showing, not just the error.
        report.append(AstPrinter.print(program)).append('\n');
        if (reporter.hasErrors()) {
            report.append("Recovered past the error(s) below and kept parsing.\n");
            return failure(report, reporter, "Parser");
        }
        Console.status(report, "Parser", true);
        report.append('\n');

        // ---- Phase 3: semantic analysis ----
        Console.banner(report, 3, "SEMANTIC ANALYSIS", "সিমান্টিক অ্যানালাইসিস");
        SemanticAnalyzer analyzer = new SemanticAnalyzer(reporter);
        analyzer.analyze(program);
        printSymbolTable(report, analyzer.declaredSymbols());
        printChecks(report, analyzer.checks());
        if (reporter.hasErrors()) {
            return failure(report, reporter, "Semantic");
        }
        report.append("Semantic analysis completed successfully.\n");
        Console.status(report, "Semantic", true);
        report.append('\n');

        // ---- Phase 4: intermediate representation, then the target code phases ----
        Path tacFile = null;
        Path javaFile = null;
        Path pythonFile = null;
        Path watFile = null;
        Path wasmFile = null;
        Path runnerFile = null;
        try {
            Files.createDirectories(outputDir);
            Map<Expr, Type> expressionTypes = analyzer.expressionTypes();
            int phase = 4;

            // TAC is generated whatever the target is: it is the compiler's own intermediate
            // representation, not something the user asked for.
            tacFile = emitTac(report, program, name, outputDir, phase++);

            if (target.includesJava()) {
                javaFile = emitJava(report, program, name, outputDir, phase++);
            }
            if (target.includesPython()) {
                pythonFile = emitPython(report, program, expressionTypes, name, outputDir, phase++);
            }
            if (target.includesWasm()) {
                Path[] written = emitWasm(report, program, expressionTypes, name, outputDir, phase);
                watFile = written[0];
                wasmFile = written[1];
                runnerFile = written[2];
            }
        } catch (IOException | RuntimeException ex) {
            // Codegen runs on an AST the semantic analyzer already validated, so a failure
            // here is either a disk problem or an internal invariant violation. Either way it
            // is reported as a failed compilation rather than a stack trace.
            report.append("Code generation failed: ").append(ex).append('\n');
            report.append('\n');
            Console.status(report, "Code generation", false);
            return new Result(false, report, tacFile, javaFile, pythonFile,
                    watFile, wasmFile, runnerFile);
        }

        return new Result(true, report, tacFile, javaFile, pythonFile,
                watFile, wasmFile, runnerFile);
    }

    // ---- Phase reports ----

    private static void printSource(Report report, String source) {
        Console.heading(report, "SOURCE CODE (উৎস কোড)");
        String[] lines = source.split("\n", -1);
        int lastLine = lines.length;
        while (lastLine > 0 && lines[lastLine - 1].isBlank()) {
            lastLine--; // do not number the trailing blank line every text file ends with
        }
        int width = String.valueOf(lastLine).length();
        for (int i = 0; i < lastLine; i++) {
            String number = Console.bn(Console.padRight(String.valueOf(i + 1), width));
            report.append(' ').append(number).append(" | ").append(lines[i].stripTrailing()).append('\n');
        }
        Console.rule(report);
        report.append('\n');
    }

    // Handed to Console.table as real headers-and-rows data, not just ASCII text: a console
    // renders it as aligned text, but a GUI can render it as a real table component instead,
    // which is the only way Bangla content (যোগফল vs দ্বিতীয়_সংখ্যা, wildly different
    // lengths) ends up genuinely lined up on screen. See Report's own comment for why.
    private static void printTokens(Report report, List<Token> tokens) {
        int count = 0;
        List<String[]> rows = new ArrayList<>();
        for (Token token : tokens) {
            boolean isEnd = token.type == TokenType.EOF;
            if (!isEnd) {
                count++;
            }
            String value = isEnd ? "(শেষ প্রান্ত)" : "'" + token.lexeme + "'";
            rows.add(new String[]{token.type.name(), value, "(লাইন " + Console.bn(token.line) + ")"});
        }
        Console.table(report, new String[]{"Token", "Value", "Position"}, rows);
        report.append('\n');
        report.append("মোট টোকেন: ").append(Console.bn(count)).append('\n');
        report.append('\n');
    }

    private static void printSymbolTable(Report report, List<Symbol> symbols) {
        report.append("Symbol Table (সিম্বল টেবিল):\n\n");
        if (symbols.isEmpty()) {
            report.append("(কোনো ভেরিয়েবল ঘোষণা করা হয়নি)\n\n");
            return;
        }
        List<String[]> rows = new ArrayList<>();
        for (Symbol symbol : symbols) {
            rows.add(new String[]{
                    symbol.name,
                    typeName(symbol.type),
                    Console.bn(symbol.declaredAtLine),
                    symbol.initialized ? "হ্যাঁ" : "না"
            });
        }
        Console.table(report, new String[]{"Name", "Type", "Line", "Initialized"}, rows);
        report.append('\n');
    }

    private static String typeName(Type type) {
        return switch (type) {
            case INT -> "পূর্ণ (int)";
            case FLOAT -> "দশমিক (float)";
            case STRING -> "বাক্য (string)";
            case BOOLEAN -> "boolean";
            case UNKNOWN -> "unknown";
        };
    }

    // One line per type or scope rule the analyzer checked and accepted. Listing them is what
    // turns "no semantic errors found" from a claim into something a reader can audit.
    private static void printChecks(Report report, List<String> checks) {
        for (String check : checks) {
            report.append("[✓] ").append(check).append('\n');
        }
        if (!checks.isEmpty()) {
            report.append('\n');
        }
    }

    private static Path emitTac(Report report, Program program, String name, Path outputDir, int phase)
            throws IOException {
        Console.banner(report, phase, "THREE ADDRESS CODE (TAC)", "ইন্টারমিডিয়েট কোড");
        List<TacInstr> instructions = new TacGenerator().generate(program);
        String listing = TacPrinter.print(instructions);
        report.append(listing).append('\n');

        Path file = outputDir.resolve(name + ".tac");
        Files.writeString(file, listing, StandardCharsets.UTF_8);
        report.append("Generated: ").append(file).append('\n');
        Console.status(report, "TAC", true);
        report.append('\n');
        return file;
    }

    private static Path emitPython(Report report, Program program, Map<Expr, Type> expressionTypes,
                                   String name, Path outputDir, int phase) throws IOException {
        Console.banner(report, phase, "PYTHON TARGET CODE", "পাইথন টার্গেট কোড");
        String pythonSource = new PythonCodeGenerator(expressionTypes).generate(program);
        report.append(pythonSource).append('\n');

        Path file = outputDir.resolve(name + ".py");
        Files.writeString(file, pythonSource, StandardCharsets.UTF_8);
        report.append("Generated: ").append(file).append('\n');
        report.append("Run it:    python ").append(file).append('\n');
        Console.status(report, "Python", true);
        report.append('\n');
        return file;
    }

    private static Path emitJava(Report report, Program program, String className, Path outputDir, int phase)
            throws IOException {
        Console.banner(report, phase, "JAVA TARGET CODE", "জাভা টার্গেট কোড");
        String javaSource = new JavaCodeGenerator().generate(program, className);
        report.append(javaSource).append('\n');

        Path file = outputDir.resolve(className + ".java");
        Files.writeString(file, javaSource, StandardCharsets.UTF_8);
        report.append("Generated: ").append(file).append('\n');
        report.append("Run it:    javac -d ").append(outputDir.resolve("out"))
                .append(' ').append(file).append(" && java -cp ")
                .append(outputDir.resolve("out")).append(' ').append(className).append('\n');
        Console.status(report, "Java", true);
        report.append('\n');
        return file;
    }

    private static Path[] emitWasm(Report report, Program program, Map<Expr, Type> expressionTypes,
                                   String moduleName, Path outputDir, int phase) throws IOException {
        Console.banner(report, phase, "WEBASSEMBLY TARGET CODE", "ওয়েবঅ্যাসেম্বলি টার্গেট কোড");
        WasmModule module = new WasmCodeGenerator(expressionTypes).generate(program, moduleName);

        String wat = WatWriter.write(module);
        report.append(wat).append('\n');

        // Three files: the readable text format, the binary module the host actually runs,
        // and the JavaScript host that supplies দেখাও and starts it.
        Path watFile = outputDir.resolve(moduleName + ".wat");
        Path wasmFile = outputDir.resolve(moduleName + ".wasm");
        Path runnerFile = outputDir.resolve(moduleName + ".mjs");
        byte[] binary = WasmBinaryWriter.write(module);
        Files.writeString(watFile, wat, StandardCharsets.UTF_8);
        Files.write(wasmFile, binary);
        Files.writeString(runnerFile, NodeRunnerGenerator.generate(moduleName), StandardCharsets.UTF_8);

        report.append("Generated: ").append(watFile).append('\n');
        report.append("Generated: ").append(wasmFile)
                .append(" (").append(Console.bn(binary.length)).append(" বাইট)\n");
        report.append("Generated: ").append(runnerFile).append('\n');
        report.append("Run it:    node ").append(runnerFile).append('\n');
        Console.status(report, "WebAssembly", true);
        report.append('\n');
        return new Path[]{watFile, wasmFile, runnerFile};
    }

    private static Result failure(Report report, ErrorReporter reporter, String phaseLabel) {
        report.append("Compilation failed:\n");
        for (var error : reporter.errors()) {
            report.append(error).append('\n');
        }
        report.append('\n');
        Console.status(report, phaseLabel, false);
        return new Result(false, report, null, null, null, null, null, null);
    }

    // Derives a valid, PascalCase Java class name from a source file's base name, since Java
    // requires the public class name to match its .java file name exactly. The same name is
    // reused for the WebAssembly output files, so one program produces one set of names.
    public static String toClassName(Path sourcePath) {
        String base = sourcePath.getFileName().toString();
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        StringBuilder sb = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : base.toCharArray()) {
            if (!Character.isLetterOrDigit(c)) {
                capitalizeNext = true;
                continue;
            }
            sb.append(capitalizeNext ? Character.toUpperCase(c) : c);
            capitalizeNext = false;
        }
        if (sb.isEmpty() || !Character.isJavaIdentifierStart(sb.charAt(0))) {
            sb.insert(0, "Ankur");
        }
        return sb.toString();
    }
}
