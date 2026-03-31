package cc.sighs.more_maid_interaction.dsl.ast;

import java.util.List;

public record BlockStmt(List<Stmt> statements) implements Stmt {
}
