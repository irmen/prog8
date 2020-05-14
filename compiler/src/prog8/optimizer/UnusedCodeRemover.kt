package prog8.optimizer

import prog8.ast.Module
import prog8.ast.Program
import prog8.ast.processing.IAstModifyingVisitor
import prog8.ast.statements.Block
import prog8.ast.statements.Subroutine


internal class UnusedCodeRemover: IAstModifyingVisitor {


    override fun visit(program: Program) {
        val callgraph = CallGraph(program)

        // remove all subroutines that aren't called, or are empty
        val removeSubroutines = mutableSetOf<Subroutine>()
        val entrypoint = program.entrypoint()
        program.modules.forEach {
            callgraph.forAllSubroutines(it) { sub ->
                if (sub !== entrypoint && !sub.keepAlways && (sub.calledBy.isEmpty() || (sub.containsNoCodeNorVars() && !sub.isAsmSubroutine)))
                    removeSubroutines.add(sub)
            }
        }

        if (removeSubroutines.isNotEmpty()) {
            removeSubroutines.forEach {
                it.definingScope().remove(it)
            }
        }

        val removeBlocks = mutableSetOf<Block>()
        program.modules.flatMap { it.statements }.filterIsInstance<Block>().forEach { block ->
            if (block.containsNoCodeNorVars() && "force_output" !in block.options())
                removeBlocks.add(block)
        }

        if (removeBlocks.isNotEmpty()) {
            removeBlocks.forEach { it.definingScope().remove(it) }
        }

        // remove modules that are not imported, or are empty (unless it's a library modules)
        val removeModules = mutableSetOf<Module>()
        program.modules.forEach {
            if (!it.isLibraryModule && (it.importedBy.isEmpty() || it.containsNoCodeNorVars()))
                removeModules.add(it)
        }

        if (removeModules.isNotEmpty()) {
            program.modules.removeAll(removeModules)
        }

        super.visit(program)
    }
}
