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

            if (!subroutine.isAsmSubroutine && !subroutine.inline && subroutine.parameters.isEmpty()) {
                val containsSubsOrVariables = subroutine.statements.any { it is VarDecl || it is Subroutine }
                if (!containsSubsOrVariables) {
                    if (subroutine.statements.size == 1 || (subroutine.statements.size == 2 && isEmptyReturn(subroutine.statements[1]))) {
                        if (subroutine !== program.entrypoint) {
                            subroutine.inline =
                                when (val stmt = subroutine.statements[0]) {
                                    is Return -> isBodyInlineable(stmt)
                                    is Assignment -> isBodyInlineable(stmt)
                                    is FunctionCallStatement -> isBodyInlineable(stmt)
                                    is Jump -> isBodyInlineable(stmt)
                                    else -> false
                                }
                        }
                    }

                    if (subroutine.inline && subroutine.statements.size > 1) {
                        require(subroutine.statements.size == 2 && isEmptyReturn(subroutine.statements[1]))
                        subroutine.statements.removeLastOrNull()      // get rid of the Return, to be able to inline the (single) statement preceding it.
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
        return if(sub==null || !canInlineAtCallSite(sub, functionCallStatement))
            noModifications
        else
            possiblyInlineFunctioncallStmt(sub, functionCallStatement, parent)
    }

    override fun before(functionCallExpr: FunctionCallExpression, parent: Node): Iterable<AstModification> {
        val sub = functionCallExpr.target.targetStatement(program.builtinFunctions) as? Subroutine

        fun inlineFunctionBody(toInline: Return): Iterable<AstModification> {
            // call site is an expression, so we have to have a Return here in the inlined sub to provide the values
            // note that we don't have to process any args, because we are currently only inlining parameterless subroutines.
            return if(toInline.values.size==1 && functionCallExpr!==toInline.values[0]) {
                sub?.hasBeenInlined=true
                listOf(AstReplaceNode(functionCallExpr, toInline.values[0].copy(), parent))
            }
            else
                noModifications
        }

        if(sub!=null && sub.inline && sub.parameters.isEmpty() && canInlineAtCallSite(sub, functionCallExpr)) {
            require(sub.statements.size == 1 || (sub.statements.size == 2 && isEmptyReturn(sub.statements[1]))) {
                "invalid inline sub at ${sub.position}"
            }
            return if(sub.isAsmSubroutine) {
                // cannot inline assembly directly in the Ast here as an Asm node is not an expression... it will be done later.
                noModifications
            } else {
                when (val toInline = sub.statements.first()) {
                    is Return -> inlineFunctionBody(toInline)
                    else -> noModifications
                }
            }
        }

        return noModifications
    }

    private fun possiblyInlineFunctioncallStmt(sub: Subroutine, origNode: Node, parent: Node): Iterable<AstModification> {

        fun possiblyShortCircuitFunctionCall(toInline: Return): Iterable<AstModification> {
            val functionCalls = toInline.values.filterIsInstance<FunctionCallExpression>()

            if (functionCalls.isEmpty()) {
                // No function calls in the return values - the void call has no side effects and can be removed.
                sub.hasBeenInlined = true
                return listOf(AstRemove(origNode as Statement, parent as IStatementContainer))
            }

            // There are function calls in the return values - convert each to a void statement.
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
            return if(origNode !== toInline) {
                sub.hasBeenInlined = true
                listOf(AstReplaceNode(origNode, toInline.copy(), parent))
            } else
                noModifications
        }

        if(sub.inline && sub.parameters.isEmpty()) {
            require(sub.statements.size == 1 || (sub.statements.size == 2 && isEmptyReturn(sub.statements[1]))) {
                "invalid inline sub at ${sub.position}"
            }
            return if(sub.isAsmSubroutine) {
                sub.hasBeenInlined=true
                listOf(AstReplaceNode(origNode, sub.statements.single().copy(), parent))
            } else {
                // note that we don't have to process any args, because we only inline parameterless subroutines.
                when (val toInline = sub.statements.first()) {
                    is Return -> possiblyShortCircuitFunctionCall(toInline)
                    else -> possiblyInlineFunctionBody(toInline)
                }
            }
        }
        return noModifications
    }

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

        sub.hasBeenInlined = true
        val scope = AnonymousScope(newAssignments.toMutableList(), assignment.position)
        return listOf(AstReplaceNode(assignment, scope, parent))
    }

    private fun canInlineAtCallSite(sub: Subroutine, fcall: IFunctionCall): Boolean {
        if (!sub.inline)
            return false
        if (options.compTarget.name != VMTarget.NAME) {
            val stmt = sub.statements.single()
            if (stmt is IFunctionCall) {
                val existing = (fcall as Node).definingScope.lookup(stmt.target.nameInSource.take(1))
                return existing !is VarDecl && existing !is StructFieldRef
            }
        }
        return true
    }
}
