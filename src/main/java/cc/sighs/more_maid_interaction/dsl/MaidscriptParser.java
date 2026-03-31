package cc.sighs.more_maid_interaction.dsl;

import cc.sighs.more_maid_interaction.dsl.ast.*;

import java.util.ArrayList;
import java.util.List;

public final class MaidscriptParser {
    private final List<Token> tokens;
    private int current;

    public MaidscriptParser(List<Token> tokens) {
        this.tokens = tokens == null ? List.of() : tokens;
        this.current = 0;
    }

    public static Program parse(String source) {
        MaidscriptLexer lexer = new MaidscriptLexer(source);
        return new MaidscriptParser(lexer.lex()).parseProgram();
    }

    public Program parseProgram() {
        List<HeaderDecl> headers = new ArrayList<>();
        while (match(TokenType.MODULE, TokenType.INCLUDE, TokenType.CONFIG)) {
            Token keyword = previous();
            switch (keyword.type()) {
                case MODULE -> {
                    String name = consume(TokenType.STRING, "Expected module name string.").literal().toString();
                    optionalTerminator();
                    headers.add(new ModuleDecl(name));
                }
                case INCLUDE -> {
                    String path = consume(TokenType.STRING, "Expected include path string.").literal().toString();
                    optionalTerminator();
                    headers.add(new IncludeDecl(path));
                }
                case CONFIG -> {
                    MapLiteralExpr config = parseMapLiteral();
                    optionalTerminator();
                    headers.add(new ConfigDecl(config));
                }
                default -> throw error(keyword, "Unexpected header keyword.");
            }
        }

        List<Declaration> declarations = new ArrayList<>();
        while (!isAtEnd()) {
            declarations.add(parseDeclaration());
        }
        return new Program(headers, declarations);
    }

    private Declaration parseDeclaration() {
        if (match(TokenType.FN)) {
            return parseFunctionDecl();
        }
        if (match(TokenType.ON_EVENT)) {
            return parseEventDecl();
        }
        throw error(peek(), "Expected declaration: 'fn' or 'on_event'.");
    }

    private FunctionDecl parseFunctionDecl() {
        String name = consume(TokenType.IDENTIFIER, "Expected function name.").lexeme();
        consume(TokenType.LPAREN, "Expected '('.");
        List<String> params = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            do {
                params.add(consume(TokenType.IDENTIFIER, "Expected parameter name.").lexeme());
            } while (match(TokenType.COMMA));
        }
        consume(TokenType.RPAREN, "Expected ')'.");
        BlockStmt body = parseBlockStmt();
        return new FunctionDecl(name, params, body);
    }

    private EventDecl parseEventDecl() {
        String eventId = consume(TokenType.STRING, "Expected event id string.").literal().toString();
        BlockStmt body = parseBlockStmt();
        return new EventDecl(eventId, body);
    }

    private BlockStmt parseBlockStmt() {
        consume(TokenType.LBRACE, "Expected '{'.");
        List<Stmt> statements = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            statements.add(parseStatement());
        }
        consume(TokenType.RBRACE, "Expected '}'.");
        optionalTerminator();
        return new BlockStmt(statements);
    }

    private Stmt parseStatement() {
        if (match(TokenType.LET)) return parseLetStmt();
        if (match(TokenType.IF)) return parseIfStmt();
        if (match(TokenType.RETURN)) return parseReturnStmt();
        if (match(TokenType.STOP)) {
            optionalTerminator();
            return new StopStmt();
        }
        if (check(TokenType.LBRACE)) {
            return parseBlockStmt();
        }

        Expr expr = parseExpression();
        if (check(TokenType.LBRACE) && supportsBlockArg(expr)) {
            MapLiteralExpr blockArg = parseMapLiteral();
            if (expr instanceof CallExpr callExpr) {
                if (callExpr.blockArg() != null) {
                    throw error(peek(), "Call already has a block argument.");
                }
                expr = new CallExpr(callExpr.callee(), callExpr.args(), blockArg);
            } else {
                expr = new CallExpr(expr, List.of(), blockArg);
            }
        }
        optionalTerminator();
        return new ExprStmt(expr);
    }

    private boolean supportsBlockArg(Expr expr) {
        return expr instanceof IdentifierExpr || expr instanceof CallExpr;
    }

    private Stmt parseLetStmt() {
        String name = consume(TokenType.IDENTIFIER, "Expected variable name after 'let'.").lexeme();
        consume(TokenType.ASSIGN, "Expected '=' after variable name.");
        Expr initializer = parseExpression();
        optionalTerminator();
        return new LetStmt(name, initializer);
    }

    private Stmt parseIfStmt() {
        Expr condition = parseExpression();
        BlockStmt thenBranch = parseBlockStmt();
        Stmt elseBranch = null;
        if (match(TokenType.ELSE)) {
            if (match(TokenType.IF)) {
                elseBranch = parseIfStmt();
            } else {
                elseBranch = parseBlockStmt();
            }
        }
        return new IfStmt(condition, thenBranch, elseBranch);
    }

    private Stmt parseReturnStmt() {
        Expr value = null;
        if (!check(TokenType.SEMICOLON) && !check(TokenType.RBRACE) && !isAtEnd()) {
            value = parseExpression();
        }
        optionalTerminator();
        return new ReturnStmt(value);
    }

    private Expr parseExpression() {
        return parseLogicalOr();
    }

    private Expr parseLogicalOr() {
        Expr expr = parseLogicalAnd();
        while (match(TokenType.OR_OR)) {
            Token op = previous();
            Expr right = parseLogicalAnd();
            expr = new BinaryExpr(expr, op.type(), right);
        }
        return expr;
    }

    private Expr parseLogicalAnd() {
        Expr expr = parseEquality();
        while (match(TokenType.AND_AND)) {
            Token op = previous();
            Expr right = parseEquality();
            expr = new BinaryExpr(expr, op.type(), right);
        }
        return expr;
    }

    private Expr parseEquality() {
        Expr expr = parseComparison();
        while (match(TokenType.EQUAL_EQUAL, TokenType.BANG_EQUAL)) {
            Token op = previous();
            Expr right = parseComparison();
            expr = new BinaryExpr(expr, op.type(), right);
        }
        return expr;
    }

    private Expr parseComparison() {
        Expr expr = parseTerm();
        while (match(TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS, TokenType.LESS_EQUAL)) {
            Token op = previous();
            Expr right = parseTerm();
            expr = new BinaryExpr(expr, op.type(), right);
        }
        return expr;
    }

    private Expr parseTerm() {
        Expr expr = parseFactor();
        while (match(TokenType.PLUS, TokenType.MINUS)) {
            Token op = previous();
            Expr right = parseFactor();
            expr = new BinaryExpr(expr, op.type(), right);
        }
        return expr;
    }

    private Expr parseFactor() {
        Expr expr = parseUnary();
        while (match(TokenType.STAR, TokenType.SLASH, TokenType.PERCENT)) {
            Token op = previous();
            Expr right = parseUnary();
            expr = new BinaryExpr(expr, op.type(), right);
        }
        return expr;
    }

    private Expr parseUnary() {
        if (match(TokenType.BANG, TokenType.MINUS)) {
            Token op = previous();
            Expr right = parseUnary();
            return new UnaryExpr(op.type(), right);
        }
        return parsePostfix();
    }

    private Expr parsePostfix() {
        Expr expr = parsePrimary();
        while (true) {
            if (match(TokenType.DOT)) {
                String member = consume(TokenType.IDENTIFIER, "Expected member name after '.'.").lexeme();
                expr = new AccessExpr(expr, member);
                continue;
            }
            if (match(TokenType.LPAREN)) {
                List<Expr> args = new ArrayList<>();
                if (!check(TokenType.RPAREN)) {
                    do {
                        args.add(parseExpression());
                    } while (match(TokenType.COMMA));
                }
                consume(TokenType.RPAREN, "Expected ')' after call arguments.");
                expr = new CallExpr(expr, args, null);
                continue;
            }
            break;
        }
        return expr;
    }

    private Expr parsePrimary() {
        if (match(TokenType.NUMBER)) return new LiteralExpr(previous().literal());
        if (match(TokenType.STRING)) return new LiteralExpr(previous().literal());
        if (match(TokenType.TRUE)) return new LiteralExpr(Boolean.TRUE);
        if (match(TokenType.FALSE)) return new LiteralExpr(Boolean.FALSE);
        if (match(TokenType.IDENTIFIER)) return new IdentifierExpr(previous().lexeme());

        if (match(TokenType.LPAREN)) {
            Expr expr = parseExpression();
            consume(TokenType.RPAREN, "Expected ')' after expression.");
            return expr;
        }

        if (check(TokenType.LBRACE)) {
            return parseMapLiteral();
        }

        throw error(peek(), "Expected expression.");
    }

    private MapLiteralExpr parseMapLiteral() {
        consume(TokenType.LBRACE, "Expected '{'.");
        List<MapEntry> entries = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            Token keyToken;
            if (match(TokenType.IDENTIFIER, TokenType.STRING)) {
                keyToken = previous();
            } else {
                throw error(peek(), "Expected map key (identifier or string).");
            }
            consume(TokenType.COLON, "Expected ':' after map key.");
            Expr value = parseExpression();
            String key = keyToken.type() == TokenType.STRING
                    ? String.valueOf(keyToken.literal())
                    : keyToken.lexeme();
            entries.add(new MapEntry(key, value));
            match(TokenType.COMMA, TokenType.SEMICOLON);
        }
        consume(TokenType.RBRACE, "Expected '}' after map literal.");
        return new MapLiteralExpr(entries);
    }

    private void optionalTerminator() {
        match(TokenType.SEMICOLON);
    }

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();
        throw error(peek(), message);
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type() == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type() == TokenType.EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private ParseException error(Token token, String message) {
        return new ParseException("[line " + token.line() + ", col " + token.column() + "] " + message);
    }
}