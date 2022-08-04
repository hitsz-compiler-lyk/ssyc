// Generated from src/frontend/SysY.g4 by ANTLR 4.9.3

package frontend;

import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link SysYParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface SysYVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link SysYParser#compUnit}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCompUnit(SysYParser.CompUnitContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysYParser#funcDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFuncDef(SysYParser.FuncDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysYParser#funcParamList}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFuncParamList(SysYParser.FuncParamListContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysYParser#funcParam}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFuncParam(SysYParser.FuncParamContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysYParser#decl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDecl(SysYParser.DeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysYParser#def}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDef(SysYParser.DefContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysYParser#initVal}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitInitVal(SysYParser.InitValContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysYParser#emptyDim}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitEmptyDim(SysYParser.EmptyDimContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysYParser#lValDecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLValDecl(SysYParser.LValDeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysYParser#stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStmt(SysYParser.StmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysYParser#block}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBlock(SysYParser.BlockContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysYParser#stmtIf}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStmtIf(SysYParser.StmtIfContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysYParser#stmtWhile}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStmtWhile(SysYParser.StmtWhileContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysYParser#stmtPutf}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStmtPutf(SysYParser.StmtPutfContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysYParser#cond}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCond(SysYParser.CondContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysYParser#exp}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExp(SysYParser.ExpContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysYParser#logOr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLogOr(SysYParser.LogOrContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysYParser#logAnd}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLogAnd(SysYParser.LogAndContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysYParser#logRel}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLogRel(SysYParser.LogRelContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysYParser#relEqOp}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRelEqOp(SysYParser.RelEqOpContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysYParser#relEq}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRelEq(SysYParser.RelEqContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysYParser#relCompOp}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRelCompOp(SysYParser.RelCompOpContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysYParser#relComp}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRelComp(SysYParser.RelCompContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysYParser#relExp}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRelExp(SysYParser.RelExpContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysYParser#expAddOp}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpAddOp(SysYParser.ExpAddOpContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysYParser#expAdd}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpAdd(SysYParser.ExpAddContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysYParser#expMulOp}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpMulOp(SysYParser.ExpMulOpContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysYParser#expMul}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpMul(SysYParser.ExpMulContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysYParser#expUnaryOp}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpUnaryOp(SysYParser.ExpUnaryOpContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysYParser#expUnary}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpUnary(SysYParser.ExpUnaryContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysYParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAtom(SysYParser.AtomContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysYParser#atomLVal}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAtomLVal(SysYParser.AtomLValContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysYParser#funcArgList}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFuncArgList(SysYParser.FuncArgListContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysYParser#lVal}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLVal(SysYParser.LValContext ctx);
}