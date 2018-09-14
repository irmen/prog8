package il65.ast

import il65.parser.ParsingFailedError

/**
 * Checks for the occurrence of recursive subroutine calls
 */

fun Module.checkRecursion(namespace: INameScope) {
    val checker = AstRecursionChecker(namespace)
    this.process(checker)
    val checkResult = checker.result()
    checkResult.forEach {
        System.err.println(it)
    }
    if(checkResult.isNotEmpty())
        throw ParsingFailedError("There are ${checkResult.size} errors in module '$name'.")
}


class DirectedGraph<VT> {
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
        graph.forEach { from, to ->
            println("$from   CALLS:")
            to.forEach { it -> println("   $it") }
        }
        val cycle = checkForCycle()
        if(cycle.isNotEmpty()) {
            println("CYCLIC!  $cycle")
        }
    }

    fun checkForCycle(): MutableList<VT> {
        val visited = uniqueVertices.associate { it to false }.toMutableMap()
        val recStack = uniqueVertices.associate { it to false }.toMutableMap()
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


class AstRecursionChecker(private val namespace: INameScope) : IAstProcessor {
    private val callGraph = DirectedGraph<INameScope>()

    fun result(): List<AstException> {
        val cycle = callGraph.checkForCycle()
        if(cycle.isEmpty())
            return emptyList()
        val chain = cycle.joinToString(" <-- ") { "${it.name} at ${it.position}" }
        return listOf(AstException("Program contains recursive subroutine calls, this is not supported. Recursive chain:\n (a subroutine call in) $chain"))
    }

    override fun process(functionCall: FunctionCallStatement): IStatement {
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
}
