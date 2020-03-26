package prog8.ast.processing

import prog8.ast.INameScope
import prog8.ast.base.ErrorReporter
import prog8.ast.base.Position
import prog8.ast.expressions.FunctionCall
import prog8.ast.statements.FunctionCallStatement
import prog8.ast.statements.Subroutine


internal class AstRecursionChecker(private val namespace: INameScope,
                                   private val errors: ErrorReporter) : IAstVisitor {
    private val callGraph = DirectedGraph<INameScope>()

    fun processMessages(modulename: String) {
        val cycle = callGraph.checkForCycle()
        if(cycle.isEmpty())
            return
        val chain = cycle.joinToString(" <-- ") { "${it.name} at ${it.position}" }
        errors.err("Program contains recursive subroutine calls, this is not supported. Recursive chain:\n (a subroutine call in) $chain", Position.DUMMY)
    }

    override fun visit(functionCallStatement: FunctionCallStatement) {
        val scope = functionCallStatement.definingScope()
        val targetStatement = functionCallStatement.target.targetStatement(namespace)
        if(targetStatement!=null) {
            val targetScope = when (targetStatement) {
                is Subroutine -> targetStatement
                else -> targetStatement.definingScope()
            }
            callGraph.add(scope, targetScope)
        }
        super.visit(functionCallStatement)
    }

    override fun visit(functionCall: FunctionCall) {
        val scope = functionCall.definingScope()
        val targetStatement = functionCall.target.targetStatement(namespace)
        if(targetStatement!=null) {
            val targetScope = when (targetStatement) {
                is Subroutine -> targetStatement
                else -> targetStatement.definingScope()
            }
            callGraph.add(scope, targetScope)
        }
        super.visit(functionCall)
    }

    private class DirectedGraph<VT> {
        private val graph = mutableMapOf<VT, MutableSet<VT>>()
        private var uniqueVertices = mutableSetOf<VT>()
        val numVertices : Int
            get() = uniqueVertices.size

        fun add(from: VT, to: VT) {
            var targets = graph[from]
            if(targets==null) {
                targets = mutableSetOf()
                graph[from] = targets
            }
            targets.add(to)
            uniqueVertices.add(from)
            uniqueVertices.add(to)
        }

        fun print() {
            println("#vertices: $numVertices")
            graph.forEach { (from, to) ->
                println("$from   CALLS:")
                to.forEach { println("   $it") }
            }
            val cycle = checkForCycle()
            if(cycle.isNotEmpty()) {
                println("CYCLIC!  $cycle")
            }
        }

        fun checkForCycle(): MutableList<VT> {
            val visited = uniqueVertices.associateWith { false }.toMutableMap()
            val recStack = uniqueVertices.associateWith { false }.toMutableMap()
            val cycle = mutableListOf<VT>()
            for(node in uniqueVertices) {
                if(isCyclicUntil(node, visited, recStack, cycle))
                    return cycle
            }
            return mutableListOf()
        }

        private fun isCyclicUntil(node: VT,
                                  visited: MutableMap<VT, Boolean>,
                                  recStack: MutableMap<VT, Boolean>,
                                  cycleNodes: MutableList<VT>): Boolean {

            if(recStack[node]==true) return true
            if(visited[node]==true) return false

            // mark current node as visited and add to recursion stack
            visited[node] = true
            recStack[node] = true

            // recurse for all neighbours
            val neighbors = graph[node]
            if(neighbors!=null) {
                for (neighbour in neighbors) {
                    if (isCyclicUntil(neighbour, visited, recStack, cycleNodes)) {
                        cycleNodes.add(node)
                        return true
                    }
                }
            }

            // pop node from recursion stack
            recStack[node] = false
            return false
        }
    }

}
