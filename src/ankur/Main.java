package ankur;

import ankur.report.Console;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

// The command-line front end. All the actual work lives in ankur.Compiler, which returns the
// phase-by-phase report as text; this class only deals with arguments and printing.
public final class Main {

    private static final Path OUTPUT_DIR = Path.of("generated");

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
        if (args.length < 1) {
            System.out.println("Usage: java -cp out ankur.Main <source-file.ank> [--target=java|python|wasm|all]");
            return;
        }

        Compiler.Target target = Compiler.Target.ALL;
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--target=")) {
                System.out.println("Unknown option: " + arg);
                return;
            }
            String value = arg.substring("--target=".length()).toUpperCase();
            try {
                target = Compiler.Target.valueOf(value);
            } catch (IllegalArgumentException unknown) {
                System.out.println("Unknown target '" + value.toLowerCase()
                        + "' (expected java, python, wasm or all)");
                return;
            }
        }

        Path path = Path.of(args[0]);
        if (!Files.exists(path)) {
            System.out.println("File not found: " + path);
            return;
        }

        String source = Files.readString(path);
        Compiler.Result result = Compiler.compile(source, Compiler.toClassName(path), target, OUTPUT_DIR);

        Console.verdict(result.report(), result.success());
        System.out.print(result.report().text());
    }
}
