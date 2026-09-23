package ankur.errors;

import ankur.report.Console;

public final class CompileError {
    private final Phase phase;
    private final int line;
    private final int column;
    private final String message;

    public CompileError(Phase phase, int line, int column, String message) {
        this.phase = phase;
        this.line = line;
        this.column = column;
        this.message = message;
    }

    public Phase phase() {
        return phase;
    }

    public int line() {
        return line;
    }

    public int column() {
        return column;
    }

    public String message() {
        return message;
    }

    // The position is printed in Bangla-Indic digits so that it matches the numbered source
    // listing the compiler prints above it, which is what a reader traces the error back to.
    @Override
    public String toString() {
        return String.format("[%s] লাইন %s:%s - %s", phase, Console.bn(line), Console.bn(column), message);
    }
}
