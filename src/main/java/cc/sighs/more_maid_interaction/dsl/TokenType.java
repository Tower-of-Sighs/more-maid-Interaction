package cc.sighs.more_maid_interaction.dsl;

public enum TokenType {
    EOF,

    IDENTIFIER,
    NUMBER,
    STRING,

    MODULE,
    INCLUDE,
    CONFIG,
    ON_EVENT,
    FN,
    LET,
    IF,
    ELSE,
    RETURN,
    STOP,
    TRUE,
    FALSE,

    LBRACE,
    RBRACE,
    LPAREN,
    RPAREN,
    COMMA,
    COLON,
    DOT,
    SEMICOLON,

    PLUS,
    MINUS,
    STAR,
    SLASH,
    PERCENT,
    BANG,
    AND_AND,
    OR_OR,
    EQUAL_EQUAL,
    BANG_EQUAL,
    GREATER,
    GREATER_EQUAL,
    LESS,
    LESS_EQUAL,
    ASSIGN
}

