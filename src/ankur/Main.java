package ankur;

import ankur.codegen.JavaCodeGenerator;
import ankur.errors.ErrorReporter;
import ankur.lexer.Lexer;
import ankur.lexer.Token;
import ankur.parser.Parser;
import ankur.parser.ast.AstPrinter;
import ankur.parser.ast.Program;
import ankur.semantic.SemanticAnalyzer;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class Main {

    public static void main(String[] args) {
        // Windows JVMs default stdout/stderr to the system codepage (e.g. Cp1252), which
        // cannot represent Bangla script and silently replaces it with '?'. Force UTF-8
        // explicitly so output is correct regardless of the host machine's locale.
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
        try {
            run(args);
        } catch (Throwable fatal) {
            // Last line of defense: the compiler must never crash with a raw stack trace.
            System.err.println("Fatal internal error: " + fatal);
        }
    }

    private static void run(String[] args) throws IOException {
        if (args.length != 1) {
            System.out.println("Usage: java -cp out ankur.Main <source-file.ank>");
            return;
        }

        Path path = Path.of(args[0]);
        if (!Files.exists(path)) {
            System.out.println("File not found: " + path);
            return;
        }

        String source = Files.readString(path);
        ErrorReporter reporter = new ErrorReporter();

        System.out.println("== Lexical Analysis ==");
        Lexer lexer = new Lexer(source, reporter);
        List<Token> tokens = lexer.scanTokens();
        System.out.println("Scanned " + tokens.size() + " tokens.");
        if (reporter.hasErrors()) {
            printErrorsAndStop(reporter, "lexical");
            return;
        }

        System.out.println();
        System.out.println("== Syntax Analysis ==");
        Parser parser = new Parser(tokens, reporter);
        Program program = parser.parseProgram();
        if (reporter.hasErrors()) {
            // Print the AST anyway: error recovery means parsing kept going past the bad
            // statement(s), and that recovered tree is worth showing, not just the error.
            System.out.println("Parsed with errors (showing the recovered AST below).");
            System.out.println();
            System.out.println(AstPrinter.print(program));
            printErrorsAndStop(reporter, "syntax");
            return;
        }
        System.out.println("Parsed successfully.");
        System.out.println();
        System.out.println(AstPrinter.print(program));

        System.out.println("== Semantic Analysis ==");
        SemanticAnalyzer analyzer = new SemanticAnalyzer(reporter);
        analyzer.analyze(program);
        if (reporter.hasErrors()) {
            printErrorsAndStop(reporter, "semantic");
            return;
        }
        System.out.println("No semantic errors found.");

        System.out.println();
        System.out.println("== Code Generation (Java) ==");
        String className = toClassName(path);
        String javaSource = new JavaCodeGenerator().generate(program, className);

        Path outputDir = Path.of("generated");
        Files.createDirectories(outputDir);
        Path outputFile = outputDir.resolve(className + ".java");
        Files.writeString(outputFile, javaSource, StandardCharsets.UTF_8);

        System.out.println("Wrote " + outputFile);
        System.out.println("Try it: javac -d generated/out " + outputFile + " && java -cp generated/out " + className);
        System.out.println();

        System.out.println("Compilation successful: no lexical, syntax, or semantic errors found.");
    }

    private static void printErrorsAndStop(ErrorReporter reporter, String phase) {
        System.out.println();
        System.out.println("Compilation failed during " + phase + " analysis:");
        reporter.printAll();
    }

    // Derives a valid, PascalCase Java class name from the source file's base name, since
    // Java requires the public class name to match its .java file name exactly.
    private static String toClassName(Path sourcePath) {
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
