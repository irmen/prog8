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


private  fun isEmptyReturn(stmt: Statement): Boolean = stmt is Return && stmt.values.size==0


// inliner potentially enables *ONE LINED* subroutines, wihtout to be inlined.

class Inliner(private val program: Program, private val options: CompilationOptions): AstWalker() {

    class DetermineInlineSubs(val program: Program): IAstVisitor {
        private val modifications = mutableListOf<IAstModification>()

        init {
            visit(program)
            modifications.forEach { it.perform() }
            modifications.clear()
        }

        override fun visit(subroutine: Subroutine) {
            if (!subroutine.isAsmSubroutine && !subroutine.inline && subroutine.parameters.isEmpty()) {
                val containsSubsOrVariables = subroutine.statements.any { it is VarDecl || it is Subroutine }
                if (!containsSubsOrVariables) {
                    if (subroutine.statements.size == 1 || (subroutine.statements.size == 2 && isEmptyReturn(subroutine.statements[1]))) {
                        if (subroutine !== program.entrypoint) {
                            // subroutine is possible candidate to be inlined
                            subroutine.inline =
                                when (val stmt = subroutine.statements[0]) {
                                    // TODO consider multi-value returns as well
                                    is Return -> stmt.values.isEmpty() || stmt.values.size==1 &&
                                        if (stmt.values[0] is NumericLiteral)
                                            true
                                        else if (stmt.values[0] is IdentifierReference) {
                                            makeFullyScoped(stmt.values[0] as IdentifierReference)
                                            true
                                        } else if (stmt.values[0]!! is IFunctionCall && (stmt.values[0] as IFunctionCall).args.size <= 1 && (stmt.values[0] as IFunctionCall).args.all { it is NumericLiteral || it is IdentifierReference }) {
                                            if (stmt.values[0] is FunctionCallExpression) {
                                                makeFullyScoped(stmt.values[0] as FunctionCallExpression)
                                                true
                                            }
                                            else false
                                        } else
                                            false

                                    is Assignment -> {
                                        if (stmt.value.isSimple) {
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

                                    is FunctionCallStatement -> {
                                        val inline =
                                            stmt.args.size <= 1 && stmt.args.all { it is NumericLiteral || it is IdentifierReference }
                                        if (inline)
                                            makeFullyScoped(stmt)
                                        inline
                                    }

                                    is Jump -> stmt.target is NumericLiteral || stmt.target is IdentifierReference
                                    else -> false
                                }
                        }
                    }

                    if (subroutine.inline && subroutine.statements.size > 1) {
                        require(subroutine.statements.size == 2 && isEmptyReturn(subroutine.statements[1]))
                        subroutine.statements.removeLast()      // get rid of the Return, to be able to inline the (single) statement preceding it.
                    }
                }
            }
            super.visit(subroutine)
        }

        private fun makeFullyScoped(identifier: IdentifierReference) {
            identifier.targetStatement(program)?.let { target ->
                val scoped = (target as INamedStatement).scopedName
                val scopedIdent = IdentifierReference(scoped, identifier.position)
                modifications += IAstModification.ReplaceNode(identifier, scopedIdent, identifier.parent)
            }
        }

        private fun makeFullyScoped(call: FunctionCallStatement) {
            makeFullyScoped(call.target)
            call.target.targetSubroutine(program)?.let { sub ->
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
            call.target.targetSubroutine(program)?.let { sub ->
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
                        val target = it.targetStatement(program) ?: return emptyList()
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

    private fun possibleInlineFcallStmt(sub: Subroutine, origNode: Node, parent: Node): Iterable<IAstModification> {
        if(sub.inline && sub.parameters.isEmpty()) {
            require(sub.statements.size == 1 || (sub.statements.size == 2 && isEmptyReturn(sub.statements[1]))) {
                "invalid inline sub at ${sub.position}"
            }
            return if(sub.isAsmSubroutine) {
                // simply insert the asm for the argument-less routine
                sub.hasBeenInlined=true
                listOf(IAstModification.ReplaceNode(origNode, sub.statements.single().copy(), parent))
            } else {
                // note that we don't have to process any args, because we online inline parameterless subroutines.
                when (val toInline = sub.statements.first()) {
                    is Return -> {
                        // TODO consider multi-value returns as well
                        if(toInline.values.size!=1)
                            noModifications
                        else {
                            val fcall = toInline.values[0] as? FunctionCallExpression
                            if(fcall!=null) {
                                // insert the function call expression as a void function call directly
                                sub.hasBeenInlined=true
                                val call = FunctionCallStatement(fcall.target.copy(), fcall.args.map { it.copy() }.toMutableList(), true, fcall.position)
                                listOf(IAstModification.ReplaceNode(origNode, call, parent))
                            } else
                                noModifications
                        }
                    }
                    else -> {
                        if(origNode !== toInline) {
                            sub.hasBeenInlined = true
                            listOf(IAstModification.ReplaceNode(origNode, toInline.copy(), parent))
                        } else
                            noModifications
                    }
                }
            }
        }
        return noModifications
    }

    override fun after(functionCallStatement: FunctionCallStatement, parent: Node): Iterable<IAstModification>  {
        val sub = functionCallStatement.target.targetStatement(program) as? Subroutine
        return if(sub==null || !canInline(sub, functionCallStatement))
            noModifications
        else
            possibleInlineFcallStmt(sub, functionCallStatement, parent)
    }

    override fun before(functionCallExpr: FunctionCallExpression, parent: Node): Iterable<IAstModification> {
        val sub = functionCallExpr.target.targetStatement(program) as? Subroutine
        if(sub!=null && sub.inline && sub.parameters.isEmpty() && canInline(sub, functionCallExpr)) {
            require(sub.statements.size == 1 || (sub.statements.size == 2 && isEmptyReturn(sub.statements[1]))) {
                "invalid inline sub at ${sub.position}"
            }
            return if(sub.isAsmSubroutine) {
                // cannot inline assembly directly in the Ast here as an Asm node is not an expression....
                noModifications
            } else {
                when (val toInline = sub.statements.first()) {
                    is Return -> {
                        // is an expression, so we have to have a Return here in the inlined sub
                        // note that we don't have to process any args, because we online inline parameterless subroutines.
                        // TODO consider multi-value returns as well
                        if(toInline.values.size==1 && functionCallExpr!==toInline.values[0]) {
                            sub.hasBeenInlined=true
                            listOf(IAstModification.ReplaceNode(functionCallExpr, toInline.values[0].copy(), parent))
                        }
                        else
                            noModifications
                    }
                    else -> noModifications
                }
            }
        }

        return noModifications
    }

    private fun canInline(sub: Subroutine, fcall: IFunctionCall): Boolean {
        if(!sub.inline)
            return false
        if(options.compTarget.name!=VMTarget.NAME) {
            val stmt = sub.statements.single()
            if (stmt is IFunctionCall) {
                val existing = (fcall as Node).definingScope.lookup(stmt.target.nameInSource.take(1))
                return existing !is VarDecl
            }
        }
        return true
    }
}

