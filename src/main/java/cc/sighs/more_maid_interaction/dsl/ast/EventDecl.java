package cc.sighs.more_maid_interaction.dsl.ast;

public record EventDecl(String eventId, BlockStmt body) implements Declaration {
}
