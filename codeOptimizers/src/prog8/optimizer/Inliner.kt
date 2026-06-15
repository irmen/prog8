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

/**
 * The Inliner performs subroutine inlining on the AST.
 *
 * It supports both manual inlining (subroutines marked with the 'inline' keyword)
 * and automatic inlining for very simple subroutines.
 *
 * Current restrictions and considerations:
 * - Automatic inlining is currently restricted to subroutines with ZERO parameters.
 * - Inlining subroutines with parameters is complicated because it requires careful
 *   parameter substitution to avoid side effects and name collisions.
 * - Handling parameterized inlining can easily lead to infinite loops or recursion
 *   in the optimizer if not done VERY CAREFULLY (e.g. self-referential subroutines).
 * - Subroutines must have a single functional statement in their body to be auto-inlined.
 * - Multi-return value subroutines are not yet supported for auto-inlining.
 */
class Inliner(private val program: Program, private val options: CompilationOptions): AstWalker() {

    inner class DetermineInlineSubs(val program: Program): IAstVisitor {
        private val modifications = mutableListOf<AstModification>()
        private val visitedSubroutines = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Subroutine, Boolean>())

        init {
            visit(program)
            modifications.forEach { it.perform() }
            modifications.clear()
        }

        private fun isRecursive(sub: Subroutine): Boolean {
            var recursive = false
            val visitor = object : IAstVisitor {
                override fun visit(functionCallExpr: FunctionCallExpression) {
                    if (!recursive && functionCallExpr.target.targetSubroutine() == sub) recursive = true
                    if (!recursive) super.visit(functionCallExpr)
                }
                override fun visit(functionCallStatement: FunctionCallStatement) {
                    if (!recursive && functionCallStatement.target.targetSubroutine() == sub) recursive = true
                    if (!recursive) super.visit(functionCallStatement)
                }
            }
            sub.statements.forEach { if (!recursive) it.accept(visitor) }
            return recursive
        }

        override fun visit(subroutine: Subroutine) {
            if (!visitedSubroutines.add(subroutine)) return

            fun isBodyInlineable(stmt: Return): Boolean {
                if (stmt.values.isEmpty())
                    return true

                return stmt.values.all { value ->
                    if (isSimpleReturnExpression(value)) {
                        val walker = object : AstWalker() {
                            override fun after(identifier: IdentifierReference, parent: Node): Iterable<AstModification> {
                                makeFullyScoped(identifier)
                                return noModifications
                            }
                        }
                        value.accept(walker, stmt)
                        true
                    } else if (value is FunctionCallExpression) {
                        if (value.args.size <= 1 && value.args.all {
                                it is NumericLiteral || it is IdentifierReference
                            }) {
                            makeFullyScoped(value)
                            true
                        } else {
                            false
                        }
                    } else {
                        false
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

            if (!subroutine.isAsmSubroutine && subroutine.parameters.isEmpty()) {
                val containsSubsOrVariables = subroutine.statements.any { it is Subroutine || it is VarDecl }
                if (!containsSubsOrVariables) {
                    val hasOnlyBodyStatements = subroutine.statements.size == 1 ||
                        (subroutine.statements.size == 2 && subroutine.statements.lastOrNull()?.let(::isEmptyReturn) == true)

                    if (hasOnlyBodyStatements) {
                        if (subroutine !== program.entrypoint) {
                            val bodyStmt = subroutine.statements.firstOrNull()
                            val isAutoInlineable =
                                when (val stmt = bodyStmt) {
                                    is Return -> isBodyInlineable(stmt)
                                    is Assignment -> isBodyInlineable(stmt)
                                    is FunctionCallStatement -> isBodyInlineable(stmt)
                                    is Jump -> isBodyInlineable(stmt)
                                    else -> false
                                }
                            if (isAutoInlineable && !isRecursive(subroutine))
                                subroutine.inline = true
                        }
                    }

                    if (subroutine.inline && subroutine.statements.size > 1) {
                        // Remove trailing return if it's empty and there's another body statement
                        if (subroutine.statements.lastOrNull()?.let(::isEmptyReturn) == true) {
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

        if (sub.parameters.isNotEmpty()) {
            return false to "subroutines with parameters cannot be inlined in this compiler version"
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
            return if (toInline.values.size == 1 && functionCallExpr !== toInline.values[0]) {
                // println(">>> INLINER: INLINED expression '${sub.name}' at ${functionCallExpr.position} (return value substituted)")
                sub.hasBeenInlined = true
                val substitutedReturn = substituteParameters(sub, functionCallExpr, toInline.values[0]) as Expression
                listOf(AstReplaceNode(functionCallExpr, substitutedReturn, parent))
            } else
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
            // Substitute parameters in the return values first
            val substitutedReturn = substituteParameters(sub, origNode as IFunctionCall, toInline) as Return
            val functionCalls = substitutedReturn.values.filterIsInstance<FunctionCallExpression>()

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
            val inlinedStatement = substituteParameters(sub, origNode as IFunctionCall, toInline) as Statement

            // println(">>> INLINER: INLINED '${sub.name}' at ${functionCallStatement.position} (body inserted)")
            return if (origNode !== toInline) {
                sub.hasBeenInlined = true
                listOf(AstReplaceNode(origNode, inlinedStatement, parent))
            } else
                noModifications
        }

        if(sub.inline) {
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
                when (val toInline = bodyStmt) {
                    is Return -> possiblyShortCircuitFunctionCall(toInline)
                    else -> possiblyInlineFunctionBody(toInline)
                }
            }
        }
        return noModifications
    }


    private fun isSimpleReturnExpression(expr: Expression): Boolean {
        return when (expr) {
            is NumericLiteral -> true
            is IdentifierReference -> true
            is PrefixExpression -> isSimpleReturnExpression(expr.expression)
            is BinaryExpression ->
                (expr.left is NumericLiteral || expr.left is IdentifierReference) &&
                        (expr.right is NumericLiteral || expr.right is IdentifierReference)
            else -> false
        }
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

        // Only handle inlining if sub is marked as such and call site is okay
        if (!sub.inline || !canInlineAtCallSite(sub, fcall))
            return noModifications

        val toInline = sub.statements.firstOrNull { it !is VarDecl || it.origin != VarDeclOrigin.SUBROUTINEPARAM } as? Return ?: return noModifications

        // Check return count matches target count
        if (toInline.values.size != multiTargets.size)
            return noModifications

        // Only allow simple return values to prevent code bloat on 6502
        if (!toInline.values.all { isSimpleReturnExpression(it) })
            return noModifications

        // Substitute parameters in all return values
        val substitutedReturn = substituteParameters(sub, fcall, toInline) as Return

        // Create multiple single assignments, skipping void targets (they discard the value)
        val newAssignments = multiTargets.zip(substitutedReturn.values)
            .filter { (target, _) -> !target.void }
            .map { (target, value) ->
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

    private fun substituteParameters(sub: Subroutine, fcall: IFunctionCall, node: Node): Node {
        val paramVarDecls = sub.statements.filterIsInstance<VarDecl>().filter { it.origin == VarDeclOrigin.SUBROUTINEPARAM }
        val paramMap: Map<VarDecl, Expression> = paramVarDecls.zip(fcall.args).associate { it.first to it.second }

        fun substitute(n: Node): Node {
            if (n is IdentifierReference) {
                val target = n.targetStatement(program.builtinFunctions)
                val arg = if (target is VarDecl) paramMap[target] else null
                if (arg != null) return arg.copy()
            }

            return when (n) {
                is BinaryExpression -> BinaryExpression(substitute(n.left) as Expression, n.operator, substitute(n.right) as Expression, n.position)
                is PrefixExpression -> PrefixExpression(n.operator, substitute(n.expression) as Expression, n.position)
                is Return -> Return(n.values.map { substitute(it) as Expression }.toTypedArray(), n.position)
                is Assignment -> {
                    val newTarget = n.target.copy()
                    if (n.target.identifier != null) {
                        val substituted = substitute(n.target.identifier!!)
                        if (substituted is IdentifierReference) {
                            newTarget.identifier = substituted
                        }
                        // if substituted is not an identifier, we leave the original (copied) identifier
                        // in the target. This is not ideal but avoids the ClassCastException.
                    }
                    Assignment(
                        newTarget,
                        substitute(n.value) as Expression,
                        n.origin,
                        n.position
                    )
                }
                is FunctionCallExpression -> FunctionCallExpression(
                    substitute(n.target) as IdentifierReference,
                    n.args.map { substitute(it) as Expression }.toMutableList(),
                    n.position
                )
                is FunctionCallStatement -> FunctionCallStatement(
                    substitute(n.target) as IdentifierReference,
                    n.args.map { substitute(it) as Expression }.toMutableList(),
                    n.void,
                    n.position
                )
                is AnonymousScope -> AnonymousScope(
                    n.statements.map { substitute(it) as Statement }.toMutableList(),
                    n.position
                )
                is Jump -> Jump(substitute(n.target) as Expression, n.position)
                else -> n.copy()
            }
        }

        return substitute(node)
    }
}
