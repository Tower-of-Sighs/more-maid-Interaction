package cc.sighs.more_maid_interaction.dsl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MaidscriptLexer {
    private static final Map<String, TokenType> KEYWORDS = new HashMap<>();

    static {
        KEYWORDS.put("module", TokenType.MODULE);
        KEYWORDS.put("include", TokenType.INCLUDE);
        KEYWORDS.put("config", TokenType.CONFIG);
        KEYWORDS.put("on_event", TokenType.ON_EVENT);
        KEYWORDS.put("fn", TokenType.FN);
        KEYWORDS.put("let", TokenType.LET);
        KEYWORDS.put("if", TokenType.IF);
        KEYWORDS.put("else", TokenType.ELSE);
        KEYWORDS.put("return", TokenType.RETURN);
        KEYWORDS.put("stop", TokenType.STOP);
        KEYWORDS.put("true", TokenType.TRUE);
        KEYWORDS.put("false", TokenType.FALSE);
    }

    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private int start;
    private int current;
    private int line;
    private int column;
    private int tokenLine;
    private int tokenColumn;

    public MaidscriptLexer(String source) {
        this.source = source == null ? "" : source;
        this.start = 0;
        this.current = 0;
        this.line = 1;
        this.column = 1;
    }

    public List<Token> lex() {
        while (!isAtEnd()) {
            start = current;
            tokenLine = line;
            tokenColumn = column;
            scanToken();
        }
        tokens.add(new Token(TokenType.EOF, "", null, line, column));
        return tokens;
    }

    private void scanToken() {
        char c = advance();
        switch (c) {
            case ' ', '\r', '\t', '\n' -> {
                // skip whitespace
            }
            case '#' -> skipLineComment();
            case '/' -> {
                if (match('*')) {
                    skipBlockComment();
                } else {
                    add(TokenType.SLASH);
                }
            }
            case '{' -> add(TokenType.LBRACE);
            case '}' -> add(TokenType.RBRACE);
            case '(' -> add(TokenType.LPAREN);
            case ')' -> add(TokenType.RPAREN);
            case ',' -> add(TokenType.COMMA);
            case ':' -> add(TokenType.COLON);
            case '.' -> add(TokenType.DOT);
            case ';' -> add(TokenType.SEMICOLON);
            case '+' -> add(TokenType.PLUS);
            case '-' -> add(TokenType.MINUS);
            case '*' -> add(TokenType.STAR);
            case '%' -> add(TokenType.PERCENT);
            case '!' -> add(match('=') ? TokenType.BANG_EQUAL : TokenType.BANG);
            case '=' -> add(match('=') ? TokenType.EQUAL_EQUAL : TokenType.ASSIGN);
            case '>' -> add(match('=') ? TokenType.GREATER_EQUAL : TokenType.GREATER);
            case '<' -> add(match('=') ? TokenType.LESS_EQUAL : TokenType.LESS);
            case '&' -> {
                if (match('&')) {
                    add(TokenType.AND_AND);
                } else {
                    error("Unexpected character '&'. Did you mean '&&'?");
                }
            }
            case '|' -> {
                if (match('|')) {
                    add(TokenType.OR_OR);
                } else {
                    error("Unexpected character '|'. Did you mean '||'?");
                }
            }
            case '"' -> string();
            default -> {
                if (isDigit(c)) {
                    number();
                } else if (isAlpha(c)) {
                    identifier();
                } else {
                    error("Unexpected character '" + c + "'.");
                }
            }
        }
    }

    private void skipLineComment() {
        while (!isAtEnd() && peek() != '\n') {
            advance();
        }
    }

    private void skipBlockComment() {
        while (!isAtEnd()) {
            if (peek() == '*' && peekNext() == '/') {
                advance();
                advance();
                return;
            }
            advance();
        }
        error("Unterminated block comment.");
    }

    private void string() {
        StringBuilder sb = new StringBuilder();
        while (!isAtEnd()) {
            char c = advance();
            if (c == '"') {
                String lexeme = source.substring(start, current);
                tokens.add(new Token(TokenType.STRING, lexeme, sb.toString(), tokenLine, tokenColumn));
                return;
            }
            if (c == '\\') {
                if (isAtEnd()) {
                    error("Unterminated escape sequence in string.");
                }
                char esc = advance();
                switch (esc) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    default -> error("Unsupported escape sequence: \\" + esc);
                }
            } else {
                sb.append(c);
            }
        }
        error("Unterminated string literal.");
    }

    private void number() {
        while (isDigit(peek())) advance();
        if (peek() == '.' && isDigit(peekNext())) {
            advance();
            while (isDigit(peek())) advance();
        }
        String text = source.substring(start, current);
        tokens.add(new Token(TokenType.NUMBER, text, Double.parseDouble(text), tokenLine, tokenColumn));
    }

    private void identifier() {
        while (isAlphaNumeric(peek())) advance();
        String text = source.substring(start, current);
        TokenType type = KEYWORDS.getOrDefault(text, TokenType.IDENTIFIER);
        tokens.add(new Token(type, text, null, tokenLine, tokenColumn));
    }

    private boolean isAtEnd() {
        return current >= source.length();
    }

    private char advance() {
        char c = source.charAt(current++);
        if (c == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }
        return c;
    }

    private boolean match(char expected) {
        if (isAtEnd()) return false;
        if (source.charAt(current) != expected) return false;
        advance();
        return true;
    }

    private char peek() {
        if (isAtEnd()) return '\0';
        return source.charAt(current);
    }

    private char peekNext() {
        if (current + 1 >= source.length()) return '\0';
        return source.charAt(current + 1);
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || c == '_';
    }

    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }

    private void add(TokenType type) {
        String lexeme = source.substring(start, current);
        tokens.add(new Token(type, lexeme, null, tokenLine, tokenColumn));
    }

    private void error(String message) {
        throw new LexerException("[line " + tokenLine + ", col " + tokenColumn + "] " + message);
    }
}
