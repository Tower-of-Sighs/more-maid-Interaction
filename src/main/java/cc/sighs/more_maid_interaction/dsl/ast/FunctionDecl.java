package cc.sighs.more_maid_interaction.dsl.ast;

import java.util.List;

public record FunctionDecl(String name, List<String> params, BlockStmt body) implements Declaration {
}
