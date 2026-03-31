package cc.sighs.more_maid_interaction.dsl.ast;

import cc.sighs.more_maid_interaction.dsl.TokenType;

public record UnaryExpr(TokenType operator, Expr right) implements Expr {
}
