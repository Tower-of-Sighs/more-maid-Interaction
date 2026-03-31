package cc.sighs.more_maid_interaction.dsl.ast;

import java.util.List;

public record CallExpr(Expr callee, List<Expr> args, MapLiteralExpr blockArg) implements Expr {
}
