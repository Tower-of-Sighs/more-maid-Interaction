package cc.sighs.more_maid_interaction.dsl.ast;

public sealed interface Expr extends Node permits LiteralExpr, IdentifierExpr, UnaryExpr, BinaryExpr, AccessExpr, CallExpr, MapLiteralExpr {
}
