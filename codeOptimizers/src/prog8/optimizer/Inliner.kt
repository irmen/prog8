package prog8.optimizer

import prog8.ast.IFunctionCall
import prog8.ast.IStatementContainer
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.*
import prog8.code.core.CompilationOptions
import prog8.code.core.InternalCompilerException
import prog8.code.target.VMTarget


private  fun isEmptyReturn(stmt: Statement): Boolean = stmt is Return && stmt.values.isEmpty()

class Inliner(private val program: Program, private val options: CompilationOptions): AstWalker() {

    class DetermineInlineSubs(val program: Program): IAstVisitor {
        private val modifications = mutableListOf<AstModification>()

        init {
            visit(program)
            modifications.forEach { it.perform() }
            modifications.clear()
        }

        override fun visit(subroutine: Subroutine) {

            fun isBodyInlineable(stmt: Return): Boolean {
                if (stmt.values.isEmpty())
                    return true

                return stmt.values.all { value ->
                    when (value) {
                        is NumericLiteral -> true
                        is IdentifierReference -> {
                            makeFullyScoped(value)
                            true
                        }
                        is FunctionCallExpression -> {
                            if (value.args.size <= 1 && value.args.all {
                                    it is NumericLiteral || it is IdentifierReference
                                }) {
                                makeFullyScoped(value)
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                }
            }

            fun isBodyInlineable(stmt: Assignment): Boolean {
                return if (stmt.value.isSimple) {
                    val targetInline =
                        if (stmt.target.identifier != null) {
                            makeFullyScoped(stmt.target.identifier!!)
                            true
                        } else if (stmt.target.memoryAddress?.addressExpression is NumericLiteral || stmt.target.memoryAddress?.addressExpression is IdentifierReference) {
                            if (stmt.target.memoryAddress?.addressExpression is IdentifierReference)
                                makeFullyScoped(stmt.target.memoryAddress?.addressExpression as IdentifierReference)
                            true
                        } else
                            false
                    val valueInline =
                        if (stmt.value is IdentifierReference) {
                            makeFullyScoped(stmt.value as IdentifierReference)
                            true
                        } else if ((stmt.value as? DirectMemoryRead)?.addressExpression is NumericLiteral || (stmt.value as? DirectMemoryRead)?.addressExpression is IdentifierReference) {
                            if ((stmt.value as? DirectMemoryRead)?.addressExpression is IdentifierReference)
                                makeFullyScoped((stmt.value as? DirectMemoryRead)?.addressExpression as IdentifierReference)
                            true
                        } else
                            false
                    targetInline || valueInline
                } else if (stmt.target.identifier != null && stmt.isAugmentable) {
                    val binExpr = stmt.value as BinaryExpression
                    if (binExpr.operator in "+-" && binExpr.right.constValue(program)?.number == 1.0) {
                        makeFullyScoped(stmt.target.identifier!!)
                        makeFullyScoped(binExpr.left as IdentifierReference)
                        true
                    } else
                        false
                } else
                    false
            }

            fun isBodyInlineable(stmt: FunctionCallStatement): Boolean {
                // Allow inlining if arguments are simple (function calls will be inlined too)
                val inline =
                    stmt.args.size <= 1 && stmt.args.all { it is NumericLiteral || it is IdentifierReference }
                if (inline)
                    makeFullyScoped(stmt)
                return inline
            }

            fun isBodyInlineable(stmt: Jump): Boolean {
                return if(stmt.target is IdentifierReference) {
                    makeFullyScoped(stmt.target as IdentifierReference)
                    true
                }
                else
                    stmt.target is NumericLiteral
            }

            if (!subroutine.isAsmSubroutine && !subroutine.inline) {
                // NOTE: We allow subroutines with ANY number of parameters to be considered for inlining.
                // For void calls where the body doesn't use the parameters, inlining is trivial -
                // we just remove the call entirely (no parameter substitution needed).
                // For calls that return values or use parameters in the body, we need simple arguments
                // (NumericLiteral or IdentifierReference) for parameter substitution to work.
                // See canInlineAtCallSite() for the argument simplicity check.

                val containsSubsOrVariables = subroutine.statements.any {
                    it is Subroutine || (it is VarDecl && it.origin != VarDeclOrigin.SUBROUTINEPARAM)
                }
                if (!containsSubsOrVariables) {
                    // For subroutines with parameters, there will be a VarDecl(SUBROUTINEPARAM) per parameter
                    // plus the body statement(s)
                    val expectedMaxStmts = subroutine.parameters.size + 1  // param VarDecls + 1 body statement
                    val hasOnlyBodyStatements = subroutine.statements.size == expectedMaxStmts ||
                        (subroutine.statements.size == expectedMaxStmts + 1 && subroutine.statements.lastOrNull()?.let(::isEmptyReturn) == true)

                    if (hasOnlyBodyStatements) {
                        if (subroutine !== program.entrypoint) {
                            // Find the first non-parameter statement (the actual body)
                            val bodyStmt = subroutine.statements.firstOrNull { it !is VarDecl || it.origin != VarDeclOrigin.SUBROUTINEPARAM }
                            subroutine.inline =
                                when (val stmt = bodyStmt) {
                                    is Return -> isBodyInlineable(stmt)
                                    is Assignment -> isBodyInlineable(stmt)
                                    is FunctionCallStatement -> isBodyInlineable(stmt)
                                    is Jump -> isBodyInlineable(stmt)
                                    else -> false
                                }
                        }
                    }

                    if (subroutine.inline && subroutine.statements.size > 1) {
                        // Remove trailing return if it's empty and there's another body statement
                        val bodyStmts = subroutine.statements.filter { it !is VarDecl || it.origin != VarDeclOrigin.SUBROUTINEPARAM }
                        if (bodyStmts.size > 1 && subroutine.statements.lastOrNull()?.let(::isEmptyReturn) == true) {
                            subroutine.statements.removeLast()
                        }
                    }
                }
            }
            super.visit(subroutine)
        }

        private fun makeFullyScoped(identifier: IdentifierReference) {
            identifier.targetStatement()?.let { target ->
                val scoped = (target as INamedStatement).scopedName
                val scopedIdent = IdentifierReference(scoped, identifier.position)
                modifications += AstReplaceNode(identifier, scopedIdent, identifier.parent)
            }
        }

        private fun makeFullyScoped(call: FunctionCallStatement) {
            makeFullyScoped(call.target)
            call.target.targetSubroutine()?.let { sub ->
                val scopedName = IdentifierReference(sub.scopedName, call.target.position)
                val scopedArgs = makeScopedArgs(call.args)
                if(scopedArgs.any()) {
                    val scopedCall = FunctionCallStatement(scopedName, scopedArgs.toMutableList(), call.void, call.position)
                    modifications += AstReplaceNode(call, scopedCall, call.parent)
                }
            }
        }

        private fun makeFullyScoped(call: FunctionCallExpression) {
            makeFullyScoped(call.target)
            call.target.targetSubroutine()?.let { sub ->
                val scopedName = IdentifierReference(sub.scopedName, call.target.position)
                val scopedArgs = makeScopedArgs(call.args)
                if(scopedArgs.any()) {
                    val scopedCall = FunctionCallExpression(scopedName, scopedArgs.toMutableList(), call.position)
                    modifications += AstReplaceNode(call, scopedCall, call.parent)
                }
            }
        }

        private fun makeScopedArgs(args: List<Expression>): List<Expression> {
            return args.map {
                when (it) {
                    is NumericLiteral -> it.copy()
                    is IdentifierReference -> {
                        val target = it.targetStatement() ?: return emptyList()
                        val scoped = (target as INamedStatement).scopedName
                        IdentifierReference(scoped, it.position)
                    }
                    else -> throw InternalCompilerException("expected only number or identifier arg, otherwise too complex")
                }
            }
        }
    }

    override fun before(program: Program): Iterable<AstModification> {
        DetermineInlineSubs(program)
        return super.before(program)
    }

    override fun after(functionCallStatement: FunctionCallStatement, parent: Node): Iterable<AstModification>  {
        val sub = functionCallStatement.target.targetStatement(program.builtinFunctions) as? Subroutine
        if (sub == null) return noModifications
        
        if (!sub.inline) return noModifications
        
        val (canInline, _) = canInlineAtCallSiteWithReason(sub, functionCallStatement)
        if (!canInline) {
            // println(">>> INLINER: NOT inlining '${sub.name}' at ${functionCallStatement.position}: $reason")
            return noModifications
        }
        
        return possiblyInlineFunctioncallStmt(sub, functionCallStatement, parent)
    }

    private fun canInlineAtCallSiteWithReason(sub: Subroutine, fcall: IFunctionCall): Pair<Boolean, String> {
        if (!sub.inline)
            return false to "subroutine not marked as inlineable"
        
        // Check parameter argument complexity
        if (sub.parameters.isNotEmpty()) {
            // Only allow inlining for void statement calls, not expression context
            if (fcall !is FunctionCallStatement || !fcall.void)
                return false to "expression context calls with parameters not supported yet"
            
            // Only inline if all arguments are simple (literals or identifiers)
            if (!fcall.args.all { it is NumericLiteral || it is IdentifierReference }) {
                val complexArgs = fcall.args.filterNot { it is NumericLiteral || it is IdentifierReference }
                return false to "complex arguments (${complexArgs.size}x): ${complexArgs.map { it::class.simpleName }.joinToString(", ")}"
            }
        }
        
        if (options.compTarget.name != VMTarget.NAME) {
            // Get the first non-parameter statement (skip VarDecl with SUBROUTINEPARAM origin)
            val bodyStmt = sub.statements.firstOrNull { it !is VarDecl || it.origin != VarDeclOrigin.SUBROUTINEPARAM }
            if (bodyStmt is IFunctionCall) {
                val existing = (fcall as Node).definingScope.lookup(bodyStmt.target.nameInSource[0])
                if (existing is VarDecl || existing is StructFieldRef) {
                    return false to "call target conflicts with existing symbol"
                }
            }
        }
        return true to "OK"
    }

    private fun canInlineAtCallSite(sub: Subroutine, fcall: IFunctionCall): Boolean {
        return canInlineAtCallSiteWithReason(sub, fcall).first
    }

    override fun before(functionCallExpr: FunctionCallExpression, parent: Node): Iterable<AstModification> {
        val sub = functionCallExpr.target.targetStatement(program.builtinFunctions) as? Subroutine
        if (sub == null) return noModifications
        if (!sub.inline) return noModifications
        
        val (canInline, _) = canInlineAtCallSiteWithReason(sub, functionCallExpr)
        if (!canInline) {
            // println(">>> INLINER: NOT inlining '${sub.name}' at ${functionCallExpr.position}: $reason")
            return noModifications
        }
        
        // Get the first non-parameter statement (skip VarDecl with SUBROUTINEPARAM origin)
        val bodyStmt = sub.statements.firstOrNull { it !is VarDecl || it.origin != VarDeclOrigin.SUBROUTINEPARAM }
        val hasOnlyBodyAndReturn = bodyStmt != null && (
            (sub.statements.count { it !is VarDecl || it.origin != VarDeclOrigin.SUBROUTINEPARAM } == 1) ||
            (sub.statements.count { it !is VarDecl || it.origin != VarDeclOrigin.SUBROUTINEPARAM } == 2 &&
             sub.statements.lastOrNull()?.let(::isEmptyReturn) == true)
        )
        if (!hasOnlyBodyAndReturn) {
            // println(">>> INLINER: NOT inlining '${sub.name}' at ${functionCallExpr.position}: body too complex")
            return noModifications
        }
        
        fun inlineFunctionBody(toInline: Return): Iterable<AstModification> {
            // call site is an expression, so we have to have a Return here in the inlined sub to provide the values
            // note that we don't have to process any args, because we are currently only inlining parameterless subroutines.
            return if(toInline.values.size==1 && functionCallExpr!==toInline.values[0]) {
                // println(">>> INLINER: INLINED expression '${sub.name}' at ${functionCallExpr.position} (return value substituted)")
                sub.hasBeenInlined =true
                listOf(AstReplaceNode(functionCallExpr, toInline.values[0].copy(), parent))
            }
            else
                noModifications
        }
        
        return if(sub.isAsmSubroutine) {
            // cannot inline assembly directly in the Ast here as an Asm node is not an expression... it will be done later.
            noModifications
        } else {
            when (val toInline = bodyStmt) {
                is Return -> inlineFunctionBody(toInline)
                else -> noModifications
            }
        }
    }

    private fun possiblyInlineFunctioncallStmt(sub: Subroutine, origNode: Node, parent: Node): Iterable<AstModification> {

        fun possiblyShortCircuitFunctionCall(toInline: Return): Iterable<AstModification> {
            val functionCalls = toInline.values.filterIsInstance<FunctionCallExpression>()

            if (functionCalls.isEmpty()) {
                // No function calls in the return values - the void call has no side effects and can be removed.
                // println(">>> INLINER: INLINED void call to '${sub.name}' at ${functionCallStatement.position} (removed, no side effects)")
                sub.hasBeenInlined = true
                return listOf(AstRemove(origNode as Statement, parent as IStatementContainer))
            }

            // There are function calls in the return values - convert each to a void statement.
            // println(">>> INLINER: INLINED void call to '${sub.name}' at ${functionCallStatement.position} (converted ${functionCalls.size} inner call(s) to void)")
            sub.hasBeenInlined = true

            val voidStatements: MutableList<Statement> = functionCalls.map { fcall ->
                FunctionCallStatement(
                    fcall.target.copy(),
                    fcall.args.map { it.copy() }.toMutableList(),
                    true,
                    fcall.position
                )
            }.toMutableList()

            // Wrap multiple statements in AnonymousScope and replace the original call node.
            val scope = AnonymousScope(voidStatements, origNode.position)
            return listOf(AstReplaceNode(origNode, scope, parent))
        }

        fun possiblyInlineFunctionBody(toInline: Statement): Iterable<AstModification> {
            // For void calls with parameters, we already verified in canInlineAtCallSiteWithReason()
            // that the parameter is unused in the body (so no substitution needed).
            // For expression context calls with parameters, those are rejected in canInlineAtCallSiteWithReason()
            // so we never reach here.
            // Just copy the statement - parameters are unused so no substitution needed.
            val inlinedStatement = toInline.copy()
            
            // println(">>> INLINER: INLINED '${sub.name}' at ${functionCallStatement.position} (body inserted, ${sub.parameters.size} param(s))")
            return if(origNode !== toInline) {
                sub.hasBeenInlined = true
                listOf(AstReplaceNode(origNode, inlinedStatement, parent))
            } else
                noModifications
        }

        if(sub.inline && sub.parameters.size >= 0) {
            // Get the first non-parameter statement (skip VarDecl with SUBROUTINEPARAM origin)
            val bodyStmt = sub.statements.firstOrNull { it !is VarDecl || it.origin != VarDeclOrigin.SUBROUTINEPARAM }
            val hasOnlyBodyAndReturn = bodyStmt != null && (
                (sub.statements.count { it !is VarDecl || it.origin != VarDeclOrigin.SUBROUTINEPARAM } == 1) ||
                (sub.statements.count { it !is VarDecl || it.origin != VarDeclOrigin.SUBROUTINEPARAM } == 2 &&
                 sub.statements.lastOrNull()?.let(::isEmptyReturn) == true)
            )
            require(hasOnlyBodyAndReturn) {
                "invalid inline sub at ${sub.position}"
            }
            return if(sub.isAsmSubroutine) {
                sub.hasBeenInlined=true
                listOf(AstReplaceNode(origNode, bodyStmt.copy(), parent))
            } else {
                // note that we don't have to process any args, because we only inline parameterless subroutines.
                when (val toInline = bodyStmt) {
                    is Return -> possiblyShortCircuitFunctionCall(toInline)
                    else -> possiblyInlineFunctionBody(toInline)
                }
            }
        }
        return noModifications
    }

    /**
     * Substitutes parameter references in a statement with argument values.
     */
    override fun after(assignment: Assignment, parent: Node): Iterable<AstModification> {
        // Handle multi-value assignments: split `a, b = func()` into separate assignments
        val multiTargets = assignment.target.multi ?: return noModifications
        val fcall = assignment.value as? FunctionCallExpression ?: return noModifications

        val sub = fcall.target.targetStatement(program.builtinFunctions) as? Subroutine
            ?: return noModifications

        // Only handle parameterless subroutines with simple returns
        if (!sub.inline || sub.parameters.isNotEmpty() || !canInlineAtCallSite(sub, fcall))
            return noModifications

        val toInline = sub.statements.firstOrNull() as? Return ?: return noModifications

        // Check return count matches target count
        if (toInline.values.size != multiTargets.size)
            return noModifications

        // Only allow simple return values (literals or identifiers, no function calls)
        if (!toInline.values.all { it is NumericLiteral || it is IdentifierReference })
            return noModifications

        // Create multiple single assignments
        val newAssignments = multiTargets.zip(toInline.values).map { (target, value) ->
            Assignment(
                target = AssignTarget(
                    identifier = target.identifier!!.copy(),
                    arrayindexed = null,
                    memoryAddress = null,
                    multi = null,
                    void = false,
                    position = target.position
                ),
                value = value.copy(),
                origin = assignment.origin,
                position = assignment.position
            )
        }

        // println(">>> INLINER: INLINED multi-return call to '${sub.name}' at ${assignment.position} (split into ${newAssignments.size} separate assignments)")
        sub.hasBeenInlined = true
        val scope = AnonymousScope(newAssignments.toMutableList(), assignment.position)
        return listOf(AstReplaceNode(assignment, scope, parent))
    }
}
