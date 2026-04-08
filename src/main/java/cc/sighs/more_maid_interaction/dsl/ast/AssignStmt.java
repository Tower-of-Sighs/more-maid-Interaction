package cc.sighs.more_maid_interaction.dsl.ast;

public record AssignStmt(String name, Expr value) implements Stmt {
}