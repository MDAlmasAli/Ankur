package ankur.tests;

import ankur.errors.ErrorReporter;
import ankur.lexer.Lexer;
import ankur.lexer.Token;
import ankur.lexer.TokenType;

import java.util.List;

import static ankur.tests.TestRunner.assertEquals;
import static ankur.tests.TestRunner.assertTrue;
import static ankur.tests.TestRunner.check;

public final class LexerTest {

    public static void runAll() {
        check("keywords শুরু/শেষ are recognized", LexerTest::keywordsRecognized);
        check("keyword যতক্ষণ is recognized", LexerTest::whileKeywordRecognized);
        check("ascii int literal", LexerTest::asciiIntLiteral);
        check("bangla-digit int literal", LexerTest::banglaIntLiteral);
        check("bangla-digit float literal", LexerTest::banglaFloatLiteral);
        check("bangla identifier is not mistaken for a keyword", LexerTest::banglaIdentifier);
        check("relational and logical operators", LexerTest::operators);
        check("line comments are skipped", LexerTest::lineComment);
        check("unknown character is reported but scanning recovers", LexerTest::unknownCharacterRecovers);
    }

    private static List<Token> lex(String source, ErrorReporter reporter) {
        return new Lexer(source, reporter).scanTokens();
    }

    private static void keywordsRecognized() {
        ErrorReporter reporter = new ErrorReporter();
        List<Token> tokens = lex("শুরু শেষ", reporter);
        assertEquals(TokenType.SHURU, tokens.get(0).type, "first token");
        assertEquals(TokenType.SHESH, tokens.get(1).type, "second token");
        assertEquals(TokenType.EOF, tokens.get(2).type, "final token");
        assertTrue(!reporter.hasErrors(), "no lexical errors expected");
    }

    private static void whileKeywordRecognized() {
        ErrorReporter reporter = new ErrorReporter();
        List<Token> tokens = lex("যতক্ষণ", reporter);
        assertEquals(TokenType.JOTOKHON, tokens.get(0).type, "token type");
        assertTrue(!reporter.hasErrors(), "no lexical errors expected");
    }

    private static void asciiIntLiteral() {
        ErrorReporter reporter = new ErrorReporter();
        List<Token> tokens = lex("123", reporter);
        assertEquals(TokenType.INT_LITERAL, tokens.get(0).type, "token type");
        assertEquals("123", tokens.get(0).lexeme, "lexeme");
    }

    private static void banglaIntLiteral() {
        ErrorReporter reporter = new ErrorReporter();
        List<Token> tokens = lex("১২৩", reporter);
        assertEquals(TokenType.INT_LITERAL, tokens.get(0).type, "token type");
        assertEquals("১২৩", tokens.get(0).lexeme, "lexeme");
    }

    private static void banglaFloatLiteral() {
        ErrorReporter reporter = new ErrorReporter();
        List<Token> tokens = lex("৩.১৪", reporter);
        assertEquals(TokenType.FLOAT_LITERAL, tokens.get(0).type, "token type");
    }

    private static void banglaIdentifier() {
        ErrorReporter reporter = new ErrorReporter();
        List<Token> tokens = lex("যোগফল", reporter);
        assertEquals(TokenType.IDENTIFIER, tokens.get(0).type, "token type");
    }

    private static void operators() {
        ErrorReporter reporter = new ErrorReporter();
        List<Token> tokens = lex("== != <= >= && || !", reporter);
        TokenType[] expected = {
                TokenType.EQ, TokenType.NEQ, TokenType.LE, TokenType.GE,
                TokenType.AND, TokenType.OR, TokenType.NOT, TokenType.EOF
        };
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], tokens.get(i).type, "token at index " + i);
        }
    }

    private static void lineComment() {
        ErrorReporter reporter = new ErrorReporter();
        List<Token> tokens = lex("1 // a comment\n2", reporter);
        assertEquals(TokenType.INT_LITERAL, tokens.get(0).type, "first token");
        assertEquals("1", tokens.get(0).lexeme, "first lexeme");
        assertEquals(TokenType.INT_LITERAL, tokens.get(1).type, "second token");
        assertEquals("2", tokens.get(1).lexeme, "second lexeme");
        assertEquals(TokenType.EOF, tokens.get(2).type, "third token");
    }

    private static void unknownCharacterRecovers() {
        ErrorReporter reporter = new ErrorReporter();
        List<Token> tokens = lex("1 @ 2", reporter);
        assertTrue(reporter.hasErrors(), "expected a lexical error for '@'");
        assertEquals(TokenType.INT_LITERAL, tokens.get(0).type, "token before bad character");
        assertEquals(TokenType.INT_LITERAL, tokens.get(1).type, "token after bad character should still be scanned");
    }
}
