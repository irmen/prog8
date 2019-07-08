package prog8.ast.processing

import prog8.ast.*
import prog8.ast.base.AstException
import prog8.ast.expressions.FunctionCall
import prog8.ast.statements.FunctionCallStatement
import prog8.ast.statements.Subroutine


internal class AstRecursionChecker(private val namespace: INameScope) : IAstProcessor {
    private val callGraph = DirectedGraph<INameScope>()

    internal fun result(): List<AstException> {
        val cycle = callGraph.checkForCycle()
        if(cycle.isEmpty())
            return emptyList()
        val chain = cycle.joinToString(" <-- ") { "${it.name} at ${it.position}" }
        return listOf(AstException("Program contains recursive subroutine calls, this is not supported. Recursive chain:\n (a subroutine call in) $chain"))
    }

    override fun process(functionCallStatement: FunctionCallStatement): IStatement {
        val scope = functionCallStatement.definingScope()
        val targetStatement = functionCallStatement.target.targetStatement(namespace)
        if(targetStatement!=null) {
            val targetScope = when (targetStatement) {
                is Subroutine -> targetStatement
                else -> targetStatement.definingScope()
            }
            callGraph.add(scope, targetScope)
        }
        return super.process(functionCallStatement)
    }

    override fun process(functionCall: FunctionCall): IExpression {
        val scope = functionCall.definingScope()
        val targetStatement = functionCall.target.targetStatement(namespace)
        if(targetStatement!=null) {
            val targetScope = when (targetStatement) {
                is Subroutine -> targetStatement
                else -> targetStatement.definingScope()
            }
            callGraph.add(scope, targetScope)
        }
        return super.process(functionCall)
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
