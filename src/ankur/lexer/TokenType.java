package ankur.lexer;

public enum TokenType {
    // Keywords
    SHURU,      // শুরু    - begin program / block
    SHESH,      // শেষ     - end program / block
    PURNO,      // পূর্ণ    - int type
    DOSHOMIK,   // দশমিক   - float type
    JODI,       // যদি     - if
    NAHOLE,     // নাহলে   - else
    JOTOKHON,   // যতক্ষণ   - while (grammar defines it; parser/semantic support lands in Phase 2)
    DEKHAO,     // দেখাও   - print

    // Literals
    IDENTIFIER,
    INT_LITERAL,
    FLOAT_LITERAL,

    // Operators
    PLUS, MINUS, STAR, SLASH, PERCENT,
    ASSIGN,
    EQ, NEQ, LT, GT, LE, GE,
    AND, OR, NOT,

    // Punctuation
    LPAREN, RPAREN, SEMI,

    EOF
}
