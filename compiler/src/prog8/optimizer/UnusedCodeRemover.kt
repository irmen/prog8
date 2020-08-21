package prog8.optimizer

import prog8.ast.INameScope
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.ErrorReporter
import prog8.ast.processing.AstWalker
import prog8.ast.processing.IAstModification
import prog8.ast.statements.*

internal class UnusedCodeRemover(private val errors: ErrorReporter): AstWalker() {

    override fun before(program: Program, parent: Node): Iterable<IAstModification> {
        val callgraph = CallGraph(program)
        val removals = mutableListOf<IAstModification>()

        // remove all subroutines that aren't called, or are empty
        val entrypoint = program.entrypoint()
        program.modules.forEach {
            callgraph.forAllSubroutines(it) { sub ->
                if (sub !== entrypoint && !sub.isAsmSubroutine && (callgraph.calledBy[sub].isNullOrEmpty() || sub.containsNoCodeNorVars())) {
                    removals.add(IAstModification.Remove(sub, sub.definingScope() as Node))
                }
            }
        }

        program.modules.flatMap { it.statements }.filterIsInstance<Block>().forEach { block ->
            if (block.containsNoCodeNorVars() && "force_output" !in block.options())
                removals.add(IAstModification.Remove(block, block.definingScope() as Node))
        }

        // remove modules that are not imported, or are empty (unless it's a library modules)
        program.modules.forEach {
            if (!it.isLibraryModule && (it.importedBy.isEmpty() || it.containsNoCodeNorVars()))
                removals.add(IAstModification.Remove(it, it.parent))
        }

        return removals
    }


    override fun before(breakStmt: Break, parent: Node): Iterable<IAstModification> {
        reportUnreachable(breakStmt, parent as INameScope)
        return emptyList()
    }

    override fun before(jump: Jump, parent: Node): Iterable<IAstModification> {
        reportUnreachable(jump, parent as INameScope)
        return emptyList()
    }

    override fun before(returnStmt: Return, parent: Node): Iterable<IAstModification> {
        reportUnreachable(returnStmt, parent as INameScope)
        return emptyList()
    }

    override fun before(functionCallStatement: FunctionCallStatement, parent: Node): Iterable<IAstModification> {
        if(functionCallStatement.target.nameInSource.last() == "exit")
            reportUnreachable(functionCallStatement, parent as INameScope)
        return emptyList()
    }

    private fun reportUnreachable(stmt: Statement, parent: INameScope) {
        when(val next = parent.nextSibling(stmt)) {
            null, is Label, is Directive, is VarDecl, is InlineAssembly, is Subroutine, is StructDecl -> {}
            else -> errors.warn("unreachable code", next.position)
        }
    }
}
