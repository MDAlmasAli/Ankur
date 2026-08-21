package ankur.errors;

import java.util.ArrayList;
import java.util.List;

public final class ErrorReporter {
    private final List<CompileError> errors = new ArrayList<>();

    public void report(Phase phase, int line, int column, String message) {
        errors.add(new CompileError(phase, line, column, message));
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public List<CompileError> errors() {
        return errors;
    }

    public void printAll() {
        for (CompileError error : errors) {
            System.out.println(error);
        }
    }
}
