package cc.sighs.more_maid_interaction.dsl.ast;

import java.util.List;

public record Program(List<HeaderDecl> headers, List<Declaration> declarations) implements Node {
}
