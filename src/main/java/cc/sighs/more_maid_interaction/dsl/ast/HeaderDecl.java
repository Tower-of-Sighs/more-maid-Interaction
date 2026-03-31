package cc.sighs.more_maid_interaction.dsl.ast;

public sealed interface HeaderDecl extends Node permits ModuleDecl, IncludeDecl, ConfigDecl {
}
