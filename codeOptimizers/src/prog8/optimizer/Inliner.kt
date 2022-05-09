package prog8.optimizer

import prog8.ast.IFunctionCall
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.expressions.FunctionCallExpression
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.ast.walk.IAstVisitor

class Inliner(val program: Program): AstWalker() {

    class DetermineInlineSubs(program: Program): IAstVisitor {
        init {
            visit(program)
        }

        override fun visit(subroutine: Subroutine) {
            if(!subroutine.isAsmSubroutine && !subroutine.inline && subroutine.parameters.isEmpty()) {
                val containsSubsOrVariables = subroutine.statements.any { it is VarDecl || it is Subroutine}
                if(!containsSubsOrVariables) {
                    if(subroutine.statements.size==1 || (subroutine.statements.size==2 && subroutine.statements[1] is Return)) {
                        // subroutine is possible candidate to be inlined
                        subroutine.inline =
                            when(val stmt=subroutine.statements[0]) {
                                is Return -> {
                                    if(stmt.value!!.isSimple) {
                                        makeFullyScoped(stmt)
                                        true
                                    } else
                                        false
                                }
                                is Assignment -> {
                                    val inline = stmt.value.isSimple && (stmt.target.identifier!=null || stmt.target.memoryAddress?.addressExpression?.isSimple==true)
                                    if(inline)
                                        makeFullyScoped(stmt)
                                    inline
                                }
                                is BuiltinFunctionCallStatement,
                                is FunctionCallStatement -> {
                                    stmt as IFunctionCall
                                    val inline = stmt.args.size<=1 && stmt.args.all { it.isSimple }
                                    if(inline)
                                        makeFullyScoped(stmt)
                                    inline
                                }
                                is PostIncrDecr -> {
                                    val inline = (stmt.target.identifier!=null || stmt.target.memoryAddress?.addressExpression?.isSimple==true)
                                    if(inline)
                                        makeFullyScoped(stmt)
                                    inline
                                }
                                is Jump, is GoSub -> true
                                else -> false
                            }
                    }
                }
            }
            super.visit(subroutine)
        }

        private fun makeFullyScoped(incrdecr: PostIncrDecr) {
            TODO("Not yet implemented")
        }

        private fun makeFullyScoped(call: IFunctionCall) {
            TODO("Not yet implemented")
        }

        private fun makeFullyScoped(assign: Assignment) {
            TODO("Not yet implemented")
        }

        private fun makeFullyScoped(ret: Return) {
            TODO("Not yet implemented")
        }
    }

    override fun before(program: Program): Iterable<IAstModification> {
        DetermineInlineSubs(program)
        return super.before(program)
    }

    override fun after(gosub: GoSub, parent: Node): Iterable<IAstModification> {
        val sub = gosub.identifier.targetStatement(program) as? Subroutine
        if(sub!=null && sub.inline) {
            val inlined = sub.statements
            TODO("INLINE GOSUB:  $gosub --->  $inlined")
        }
        return noModifications
    }

    override fun after(functionCallExpr: FunctionCallExpression, parent: Node): Iterable<IAstModification> = inlineCall(functionCallExpr as IFunctionCall, parent)

    override fun after(functionCallStatement: FunctionCallStatement, parent: Node): Iterable<IAstModification> = inlineCall(functionCallStatement as IFunctionCall, parent)

    private fun inlineCall(call: IFunctionCall, parent: Node): Iterable<IAstModification> {
        val sub = call.target.targetStatement(program) as? Subroutine
        if(sub!=null && sub.inline) {
            val inlined = sub.statements
            TODO("INLINE FCALL:  $call --->  $inlined")
        }
        return noModifications
    }
}

