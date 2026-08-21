package ankur.parser.ast;

public record NumberLiteral(boolean isFloat, long intValue, double floatValue, String text, int line, int column)
        implements Expr {

    public static NumberLiteral ofInt(long value, String text, int line, int column) {
        return new NumberLiteral(false, value, 0.0, text, line, column);
    }

    public static NumberLiteral ofFloat(double value, String text, int line, int column) {
        return new NumberLiteral(true, 0L, value, text, line, column);
    }
}
