package cc.sighs.more_maid_interaction.dsl;

public record Token(TokenType type, String lexeme, Object literal, int line, int column) {
}
