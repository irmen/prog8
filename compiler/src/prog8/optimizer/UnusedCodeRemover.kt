package prog8.optimizer

import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.processing.AstWalker
import prog8.ast.processing.IAstModification
import prog8.ast.statements.Block

/*
    TODO: remove unreachable code after return and exit()
*/

internal class UnusedCodeRemover: AstWalker() {

    override fun before(program: Program, parent: Node): Iterable<IAstModification> {
        val callgraph = CallGraph(program)
        val removals = mutableListOf<IAstModification>()

        // remove all subroutines that aren't called, or are empty
        val entrypoint = program.entrypoint()
        program.modules.forEach {
            callgraph.forAllSubroutines(it) { sub ->
                if (sub !== entrypoint && !sub.keepAlways && (callgraph.calledBy[sub].isNullOrEmpty() || (sub.containsNoCodeNorVars() && !sub.isAsmSubroutine)))
                    removals.add(IAstModification.Remove(sub, sub.definingScope() as Node))
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
}
