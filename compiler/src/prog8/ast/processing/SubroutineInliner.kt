package prog8.ast.processing

import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.statements.*
import prog8.optimizer.CallGraph


internal class SubroutineInliner(private val program: Program) : AstWalker() {
    private val noModifications = emptyList<IAstModification>()
    private val callgraph = CallGraph(program)

    override fun after(subroutine: Subroutine, parent: Node): Iterable<IAstModification> {

        if(!subroutine.isAsmSubroutine && callgraph.calledBy[subroutine]!=null && subroutine.containsCodeOrVars()) {

            // TODO for now, inlined subroutines  can't have parameters or local variables - improve this
            if(subroutine.parameters.isEmpty() && subroutine.containsNoVars()) {
                if (subroutine.countStatements() <= 5) {
                    if (callgraph.calledBy.getValue(subroutine).size == 1 || !subroutine.statements.any { it.expensiveToInline })
                        return inline(subroutine)
                }
            }
        }
        return noModifications
    }

    private fun inline(subroutine: Subroutine): Iterable<IAstModification> {
        val calls = callgraph.calledBy.getValue(subroutine)
        return calls.map {
            call -> IAstModification.ReplaceNode(
                call,
                AnonymousScope(subroutine.statements, call.position),
                call.parent
            )
        }.plus(IAstModification.Remove(subroutine, subroutine.parent))
    }

}
