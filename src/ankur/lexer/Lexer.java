package ankur.lexer;

import ankur.errors.ErrorReporter;
import ankur.errors.Phase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Lexer {

    private static final Map<String, TokenType> KEYWORDS = new HashMap<>();

    static {
        KEYWORDS.put("শুরু", TokenType.SHURU);
        KEYWORDS.put("শেষ", TokenType.SHESH);
        KEYWORDS.put("পূর্ণ", TokenType.PURNO);
        KEYWORDS.put("দশমিক", TokenType.DOSHOMIK);
        KEYWORDS.put("যদি", TokenType.JODI);
        KEYWORDS.put("নাহলে", TokenType.NAHOLE);
        KEYWORDS.put("যতক্ষণ", TokenType.JOTOKHON);
        KEYWORDS.put("দেখাও", TokenType.DEKHAO);
    }

    private final String source;
    private final ErrorReporter reporter;

    private int pos = 0;
    private int line = 1;
    private int col = 1;

    public Lexer(String source, ErrorReporter reporter) {
        this.source = source;
        this.reporter = reporter;
    }

    public List<Token> scanTokens() {
        List<Token> tokens = new ArrayList<>();
        Token token;
        do {
            token = nextToken();
            tokens.add(token);
        } while (token.type != TokenType.EOF);
        return tokens;
    }

    private Token nextToken() {
        skipWhitespaceAndComments();

        int startLine = line;
        int startCol = col;

        if (isAtEnd()) {
            return new Token(TokenType.EOF, "", startLine, startCol);
        }

        char c = advance();

        if (isIdentifierStart(c)) {
            return scanIdentifierOrKeyword(c, startLine, startCol);
        }
        if (isDigit(c)) {
            return scanNumber(c, startLine, startCol);
        }

        switch (c) {
            case '+': return make(TokenType.PLUS, "+", startLine, startCol);
            case '-': return make(TokenType.MINUS, "-", startLine, startCol);
            case '*': return make(TokenType.STAR, "*", startLine, startCol);
            case '/': return make(TokenType.SLASH, "/", startLine, startCol);
            case '%': return make(TokenType.PERCENT, "%", startLine, startCol);
            case '(': return make(TokenType.LPAREN, "(", startLine, startCol);
            case ')': return make(TokenType.RPAREN, ")", startLine, startCol);
            case ';': return make(TokenType.SEMI, ";", startLine, startCol);
            case '=':
                if (match('=')) return make(TokenType.EQ, "==", startLine, startCol);
                return make(TokenType.ASSIGN, "=", startLine, startCol);
            case '!':
                if (match('=')) return make(TokenType.NEQ, "!=", startLine, startCol);
                return make(TokenType.NOT, "!", startLine, startCol);
            case '<':
                if (match('=')) return make(TokenType.LE, "<=", startLine, startCol);
                return make(TokenType.LT, "<", startLine, startCol);
            case '>':
                if (match('=')) return make(TokenType.GE, ">=", startLine, startCol);
                return make(TokenType.GT, ">", startLine, startCol);
            case '&':
                if (match('&')) return make(TokenType.AND, "&&", startLine, startCol);
                reporter.report(Phase.LEXICAL, startLine, startCol, "Unexpected character '&' (did you mean '&&'?)");
                return nextToken();
            case '|':
                if (match('|')) return make(TokenType.OR, "||", startLine, startCol);
                reporter.report(Phase.LEXICAL, startLine, startCol, "Unexpected character '|' (did you mean '||'?)");
                return nextToken();
            default:
                reporter.report(Phase.LEXICAL, startLine, startCol, "Unexpected character '" + c + "'");
                return nextToken();
        }
    }

    private Token scanIdentifierOrKeyword(char first, int startLine, int startCol) {
        StringBuilder sb = new StringBuilder();
        sb.append(first);
        while (!isAtEnd() && isIdentifierPart(peek())) {
            sb.append(advance());
        }
        String text = sb.toString();
        TokenType type = KEYWORDS.getOrDefault(text, TokenType.IDENTIFIER);
        return make(type, text, startLine, startCol);
    }

    private Token scanNumber(char first, int startLine, int startCol) {
        StringBuilder sb = new StringBuilder();
        sb.append(first);
        while (!isAtEnd() && isDigit(peek())) {
            sb.append(advance());
        }
        boolean isFloat = false;
        if (!isAtEnd() && peek() == '.' && pos + 1 < source.length() && isDigit(source.charAt(pos + 1))) {
            isFloat = true;
            sb.append(advance()); // consume '.'
            while (!isAtEnd() && isDigit(peek())) {
                sb.append(advance());
            }
        }
        return make(isFloat ? TokenType.FLOAT_LITERAL : TokenType.INT_LITERAL, sb.toString(), startLine, startCol);
    }

    private void skipWhitespaceAndComments() {
        while (!isAtEnd()) {
            char c = peek();
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                advance();
            } else if (c == '/' && pos + 1 < source.length() && source.charAt(pos + 1) == '/') {
                while (!isAtEnd() && peek() != '\n') {
                    advance();
                }
            } else {
                break;
            }
        }
    }

    private boolean isAtEnd() {
        return pos >= source.length();
    }

    private char peek() {
        return source.charAt(pos);
    }

    private char advance() {
        char c = source.charAt(pos);
        pos++;
        if (c == '\n') {
            line++;
            col = 1;
        } else {
            col++;
        }
        return c;
    }

    private boolean match(char expected) {
        if (isAtEnd() || peek() != expected) {
            return false;
        }
        advance();
        return true;
    }

    private Token make(TokenType type, String lexeme, int startLine, int startCol) {
        return new Token(type, lexeme, startLine, startCol);
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isBanglaDigit(char c) {
        return c >= '০' && c <= '৯'; // Bengali digits 0..9 (০-৯)
    }

    private static boolean isBanglaLetter(char c) {
        return c >= 'ঀ' && c <= '৿' && !isBanglaDigit(c); // full Bengali Unicode block
    }

    private static boolean isDigit(char c) {
        return isAsciiDigit(c) || isBanglaDigit(c);
    }

    private static boolean isIdentifierStart(char c) {
        return isAsciiLetter(c) || isBanglaLetter(c);
    }

    private static boolean isIdentifierPart(char c) {
        return isIdentifierStart(c) || isDigit(c);
    }
}
