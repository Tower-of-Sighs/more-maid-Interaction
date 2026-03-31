package cc.sighs.more_maid_interaction.dsl.ast;

import java.util.List;

public record MapLiteralExpr(List<MapEntry> entries) implements Expr {
}
