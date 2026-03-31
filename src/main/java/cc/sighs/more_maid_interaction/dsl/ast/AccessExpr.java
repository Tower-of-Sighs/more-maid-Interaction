package cc.sighs.more_maid_interaction.dsl.ast;

public record AccessExpr(Expr target, String member) implements Expr {
}
