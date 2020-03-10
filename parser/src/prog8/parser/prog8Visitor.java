// Generated from /home/irmen/Projects/prog8/parser/antlr/prog8.g4 by ANTLR 4.8

package prog8.parser;

import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link prog8Parser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface prog8Visitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link prog8Parser#module}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitModule(prog8Parser.ModuleContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#modulestatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitModulestatement(prog8Parser.ModulestatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#block}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBlock(prog8Parser.BlockContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatement(prog8Parser.StatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#labeldef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLabeldef(prog8Parser.LabeldefContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#unconditionaljump}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUnconditionaljump(prog8Parser.UnconditionaljumpContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#directive}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDirective(prog8Parser.DirectiveContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#directivearg}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDirectivearg(prog8Parser.DirectiveargContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#vardecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVardecl(prog8Parser.VardeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#structvardecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStructvardecl(prog8Parser.StructvardeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#varinitializer}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVarinitializer(prog8Parser.VarinitializerContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#structvarinitializer}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStructvarinitializer(prog8Parser.StructvarinitializerContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#constdecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConstdecl(prog8Parser.ConstdeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#memoryvardecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMemoryvardecl(prog8Parser.MemoryvardeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#structdecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStructdecl(prog8Parser.StructdeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#datatype}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDatatype(prog8Parser.DatatypeContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#arrayindex}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArrayindex(prog8Parser.ArrayindexContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#assignment}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAssignment(prog8Parser.AssignmentContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#augassignment}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAugassignment(prog8Parser.AugassignmentContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#assign_target}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAssign_target(prog8Parser.Assign_targetContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#postincrdecr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPostincrdecr(prog8Parser.PostincrdecrContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#expression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpression(prog8Parser.ExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#typecast}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTypecast(prog8Parser.TypecastContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#arrayindexed}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArrayindexed(prog8Parser.ArrayindexedContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#directmemory}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDirectmemory(prog8Parser.DirectmemoryContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#addressof}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAddressof(prog8Parser.AddressofContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#functioncall}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFunctioncall(prog8Parser.FunctioncallContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#functioncall_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFunctioncall_stmt(prog8Parser.Functioncall_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#expression_list}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpression_list(prog8Parser.Expression_listContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#returnstmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitReturnstmt(prog8Parser.ReturnstmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#breakstmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBreakstmt(prog8Parser.BreakstmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#continuestmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitContinuestmt(prog8Parser.ContinuestmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#identifier}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIdentifier(prog8Parser.IdentifierContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#scoped_identifier}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitScoped_identifier(prog8Parser.Scoped_identifierContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#register}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRegister(prog8Parser.RegisterContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#registerorpair}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRegisterorpair(prog8Parser.RegisterorpairContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#statusregister}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatusregister(prog8Parser.StatusregisterContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#integerliteral}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIntegerliteral(prog8Parser.IntegerliteralContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#wordsuffix}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitWordsuffix(prog8Parser.WordsuffixContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#booleanliteral}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBooleanliteral(prog8Parser.BooleanliteralContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#arrayliteral}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArrayliteral(prog8Parser.ArrayliteralContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#structliteral}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStructliteral(prog8Parser.StructliteralContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#stringliteral}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStringliteral(prog8Parser.StringliteralContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#charliteral}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCharliteral(prog8Parser.CharliteralContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#floatliteral}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFloatliteral(prog8Parser.FloatliteralContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#literalvalue}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLiteralvalue(prog8Parser.LiteralvalueContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#inlineasm}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitInlineasm(prog8Parser.InlineasmContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#subroutine}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSubroutine(prog8Parser.SubroutineContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#sub_return_part}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSub_return_part(prog8Parser.Sub_return_partContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#statement_block}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatement_block(prog8Parser.Statement_blockContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#sub_params}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSub_params(prog8Parser.Sub_paramsContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#sub_returns}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSub_returns(prog8Parser.Sub_returnsContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#asmsubroutine}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAsmsubroutine(prog8Parser.AsmsubroutineContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#asmsub_address}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAsmsub_address(prog8Parser.Asmsub_addressContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#asmsub_params}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAsmsub_params(prog8Parser.Asmsub_paramsContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#asmsub_param}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAsmsub_param(prog8Parser.Asmsub_paramContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#asmsub_clobbers}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAsmsub_clobbers(prog8Parser.Asmsub_clobbersContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#clobber}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitClobber(prog8Parser.ClobberContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#asmsub_returns}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAsmsub_returns(prog8Parser.Asmsub_returnsContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#asmsub_return}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAsmsub_return(prog8Parser.Asmsub_returnContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#if_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIf_stmt(prog8Parser.If_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#else_part}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitElse_part(prog8Parser.Else_partContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#branch_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBranch_stmt(prog8Parser.Branch_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#branchcondition}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBranchcondition(prog8Parser.BranchconditionContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#forloop}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitForloop(prog8Parser.ForloopContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#whileloop}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitWhileloop(prog8Parser.WhileloopContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#repeatloop}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRepeatloop(prog8Parser.RepeatloopContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#whenstmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitWhenstmt(prog8Parser.WhenstmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link prog8Parser#when_choice}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitWhen_choice(prog8Parser.When_choiceContext ctx);
}