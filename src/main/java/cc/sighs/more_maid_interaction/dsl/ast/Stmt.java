package cc.sighs.more_maid_interaction.dsl.ast;

public sealed interface Stmt extends Node permits BlockStmt, LetStmt, AssignStmt, IfStmt, ReturnStmt, StopStmt, ExprStmt {
}