package cc.sighs.more_maid_interaction.dsl.ast;

public record LetStmt(String name, Expr initializer) implements Stmt {
}
