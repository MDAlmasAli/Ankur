package ankur.lexer;

public enum TokenType {
    // Keywords
    SHURU,      // শুরু    - begin program / block
    SHESH,      // শেষ     - end program / block
    PURNO,      // পূর্ণ    - int type
    DOSHOMIK,   // দশমিক   - float type
    BAKKO,      // বাক্য    - string type
    JODI,       // যদি     - if
    NAHOLE,     // নাহলে   - else
    JOTOKHON,   // যতক্ষণ   - while
    DEKHAO,     // দেখাও   - print

    // Literals
    IDENTIFIER,
    INT_LITERAL,
    FLOAT_LITERAL,
    STRING_LITERAL,

    // Operators
    PLUS, MINUS, STAR, SLASH, PERCENT,
    ASSIGN,
    EQ, NEQ, LT, GT, LE, GE,
    AND, OR, NOT,

    // Punctuation
    LPAREN, RPAREN, SEMI,

    EOF
}
