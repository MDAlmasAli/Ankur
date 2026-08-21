package ankur.errors;

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

    @Override
    public String toString() {
        return String.format("[%s] line %d:%d - %s", phase, line, column, message);
    }
}
