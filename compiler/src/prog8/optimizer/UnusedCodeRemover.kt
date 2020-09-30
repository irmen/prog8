package prog8.optimizer

import prog8.ast.INameScope
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.ErrorReporter
import prog8.ast.expressions.NumericLiteralValue
import prog8.ast.processing.AstWalker
import prog8.ast.processing.IAstModification
import prog8.ast.statements.*


// TODO remove unneeded assignments such as:  cc = 0 ;   cc= xbuf  ; ...    the first can be removed (unless target is not RAM)

internal class UnusedCodeRemover(private val program: Program, private val errors: ErrorReporter): AstWalker() {

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

    override fun after(scope: AnonymousScope, parent: Node): Iterable<IAstModification> {
        val removeDoubleAssignments = deduplicateAssignments(scope.statements)
        return removeDoubleAssignments.map { IAstModification.Remove(it, scope) }
    }

    override fun after(block: Block, parent: Node): Iterable<IAstModification> {
        val removeDoubleAssignments = deduplicateAssignments(block.statements)
        return removeDoubleAssignments.map { IAstModification.Remove(it, block) }
    }

    override fun after(subroutine: Subroutine, parent: Node): Iterable<IAstModification> {
        val removeDoubleAssignments = deduplicateAssignments(subroutine.statements)
        return removeDoubleAssignments.map { IAstModification.Remove(it, subroutine) }
    }

    // subroutine, anonscope,
    private fun deduplicateAssignments(statements: List<Statement>): List<Assignment> {
        // removes 'duplicate' assignments that assign the isSameAs target
        val linesToRemove = mutableListOf<Assignment>()

        for (stmtPairs in statements.windowed(2, step = 1)) {
            val assign1 = stmtPairs[0] as? Assignment
            val assign2 = stmtPairs[1] as? Assignment
            if (assign1 != null && assign2 != null) {
                if (assign1.target.isSameAs(assign2.target, program) && assign1.target.isInRegularRAM(program.namespace))
                    linesToRemove.add(assign1)
            }
        }

        return linesToRemove
    }
}
