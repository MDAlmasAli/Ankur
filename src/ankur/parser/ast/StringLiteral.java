package ankur.parser.ast;

// A বাক্য literal. `value` is the decoded text (escapes already resolved by the Lexer);
// `text` is how it should be shown in a report, quotes included.
public record StringLiteral(String value, int line, int column) implements Expr {

    public String text() {
        return "\"" + value + "\"";
    }
}
