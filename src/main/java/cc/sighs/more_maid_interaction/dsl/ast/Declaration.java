package cc.sighs.more_maid_interaction.dsl.ast;

public sealed interface Declaration extends Node permits FunctionDecl, EventDecl {
}
