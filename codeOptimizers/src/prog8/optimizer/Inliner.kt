package prog8.optimizer

import prog8.ast.IFunctionCall
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.ast.walk.IAstVisitor
import prog8.code.core.CompilationOptions
import prog8.code.core.InternalCompilerException
import prog8.code.target.VMTarget


private  fun isEmptyReturn(stmt: Statement): Boolean = stmt is Return && stmt.values.isEmpty()


// inliner potentially enables *ONE LINED* subroutines, without to be inlined.

class Inliner(private val program: Program, private val options: CompilationOptions): AstWalker() {

    class DetermineInlineSubs(val program: Program): IAstVisitor {
        private val modifications = mutableListOf<IAstModification>()

        init {
            visit(program)
            modifications.forEach { it.perform() }
            modifications.clear()
        }

        override fun visit(subroutine: Subroutine) {
            
            fun shouldInline(stmt: Return): Boolean {
                // TODO consider multi-value returns as well for possible inlining
                return stmt.values.isEmpty() || stmt.values.size==1 &&
                    if (stmt.values[0] is NumericLiteral)
                        true
                    else if (stmt.values[0] is IdentifierReference) {
                        makeFullyScoped(stmt.values[0] as IdentifierReference)
                        true
                    } else if (stmt.values[0] is IFunctionCall && (stmt.values[0] as IFunctionCall).args.size <= 1 && (stmt.values[0] as IFunctionCall).args.all { it is NumericLiteral || it is IdentifierReference }) {
                        if (stmt.values[0] is FunctionCallExpression) {
                            makeFullyScoped(stmt.values[0] as FunctionCallExpression)
                            true
                        } else false
                    } else
                        false
            }
            
            fun shouldInline(stmt: Assignment): Boolean {
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
            
            fun shouldInline(stmt: FunctionCallStatement): Boolean {
                val inline =
                    stmt.args.size <= 1 && stmt.args.all { it is NumericLiteral || it is IdentifierReference }
                if (inline)
                    makeFullyScoped(stmt)
                return inline
            }
            
            fun shouldInline(stmt: Jump): Boolean {
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
                            // subroutine is possible candidate to be inlined: evaluate what is in it and if we can inline that
                            subroutine.inline =
                                when (val stmt = subroutine.statements[0]) {
                                    is Return -> shouldInline(stmt)
                                    is Assignment -> shouldInline(stmt)
                                    is FunctionCallStatement -> shouldInline(stmt)
                                    is Jump -> shouldInline(stmt)
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
                modifications += IAstModification.ReplaceNode(identifier, scopedIdent, identifier.parent)
            }
        }

        private fun makeFullyScoped(call: FunctionCallStatement) {
            makeFullyScoped(call.target)
            call.target.targetSubroutine()?.let { sub ->
                val scopedName = IdentifierReference(sub.scopedName, call.target.position)
                val scopedArgs = makeScopedArgs(call.args)
                if(scopedArgs.any()) {
                    val scopedCall = FunctionCallStatement(scopedName, scopedArgs.toMutableList(), call.void, call.position)
                    modifications += IAstModification.ReplaceNode(call, scopedCall, call.parent)
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
                    modifications += IAstModification.ReplaceNode(call, scopedCall, call.parent)
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

    override fun before(program: Program): Iterable<IAstModification> {
        DetermineInlineSubs(program)
        return super.before(program)
    }

    override fun after(functionCallStatement: FunctionCallStatement, parent: Node): Iterable<IAstModification>  {
        val sub = functionCallStatement.target.targetStatement(program.builtinFunctions) as? Subroutine
        return if(sub==null || !canInline(sub, functionCallStatement))
            noModifications
        else
            possiblyInlineFunctioncallStmt(sub, functionCallStatement, parent)
    }

    override fun before(functionCallExpr: FunctionCallExpression, parent: Node): Iterable<IAstModification> {
        val sub = functionCallExpr.target.targetStatement(program.builtinFunctions) as? Subroutine
        
        fun inlineFunctionBody(toInline: Return): Iterable<IAstModification> {
            // call site is an expression, so we have to have a Return here in the inlined sub to provide the values
            // note that we don't have to process any args, because we are currently only inlining parameterless subroutines.
            // TODO consider multi-value returns as well, but the values can be anything...
            return if(toInline.values.size==1 && functionCallExpr!==toInline.values[0]) {
                sub?.hasBeenInlined=true
                listOf(IAstModification.ReplaceNode(functionCallExpr, toInline.values[0].copy(), parent))
            }
            else
                noModifications
        }
        
        if(sub!=null && sub.inline && sub.parameters.isEmpty() && canInline(sub, functionCallExpr)) {
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

    private fun possiblyInlineFunctioncallStmt(sub: Subroutine, origNode: Node, parent: Node): Iterable<IAstModification> {
        
        fun possiblyShortCircuitFunctionCall(toInline: Return): Iterable<IAstModification> {
            return if(toInline.values.size!=1)
                noModifications  // TODO consider multi-value return statements as well, but those could contain anything in each value...
            else {
                val fcall = toInline.values[0] as? FunctionCallExpression
                if(fcall!=null) {
                    // insert the returned function call expression as a void function call statement directly 
                    // (at the call site there is no target to put any return values into when inlining such calls as part of a function call statement)
                    sub.hasBeenInlined=true
                    val call = FunctionCallStatement(fcall.target.copy(), fcall.args.map { it.copy() }.toMutableList(), true, fcall.position)
                    listOf(IAstModification.ReplaceNode(origNode, call, parent))
                } else
                    noModifications
            }
        }
        
        fun possiblyInlineFunctionBody(toInline: Statement): Iterable<IAstModification> {
            return if(origNode !== toInline) {
                sub.hasBeenInlined = true
                listOf(IAstModification.ReplaceNode(origNode, toInline.copy(), parent))
            } else
                noModifications
        }
        
        if(sub.inline && sub.parameters.isEmpty()) {
            require(sub.statements.size == 1 || (sub.statements.size == 2 && isEmptyReturn(sub.statements[1]))) {
                "invalid inline sub at ${sub.position}"
            }
            return if(sub.isAsmSubroutine) {
                // simply insert the asm for the argument-less routine
                sub.hasBeenInlined=true
                listOf(IAstModification.ReplaceNode(origNode, sub.statements.single().copy(), parent))
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

    private fun canInline(sub: Subroutine, fcall: IFunctionCall): Boolean {
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

