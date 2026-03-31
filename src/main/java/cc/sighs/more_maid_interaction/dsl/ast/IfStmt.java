package cc.sighs.more_maid_interaction.dsl.ast;

public record IfStmt(Expr condition, BlockStmt thenBranch, Stmt elseBranch) implements Stmt {
}
