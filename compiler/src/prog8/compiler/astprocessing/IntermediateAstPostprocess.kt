package prog8.compiler.astprocessing

import prog8.code.SymbolTable
import prog8.code.ast.*
import prog8.code.core.DataType
import prog8.code.core.IErrorReporter

internal fun postprocessIntermediateAst(program: PtProgram, st: SymbolTable, errors: IErrorReporter) {
    coalesceDefers(program)
    integrateDefers(program, st)
}


private fun coalesceDefers(program: PtProgram) {
    val defersPerSub = mutableMapOf<PtSub, MutableList<PtDefer>>().withDefault { mutableListOf() }

    walkAst(program) { node, _ ->
        if(node is PtDefer) {
            val scope = node.definingSub()!!
            val defers = defersPerSub.getValue(scope)
            defers.add(node)
            defersPerSub[scope] = defers
        }
    }

    for((sub, defers) in defersPerSub) {
        val coalescedDefer = PtDefer(sub.position)
        for(defer in defers.reversed()) {
            for(stmt in defer.children)
                coalescedDefer.add(stmt)
            sub.children.remove(defer)
        }

        if(coalescedDefer.children.isNotEmpty()) {
            sub.add(coalescedDefer)
        }
    }
}


private fun integrateDefers(program: PtProgram, st: SymbolTable) {
    val exitsToAugment = mutableListOf<PtNode>()
    val subEndsToAugment = mutableListOf<PtSub>()

    walkAst(program) { node, _ ->
        when(node) {
            is PtJump -> {
                if(node.identifier!=null) {
                    val stNode = st.lookup(node.identifier!!.name)!!
                    val targetSub = stNode.astNode.definingSub()
                    if(targetSub!=node.definingSub())
                        exitsToAugment.add(node)
                }
            }
            is PtReturn -> exitsToAugment.add(node)
            is PtSub -> {
                val lastStmt = node.children.lastOrNull { it !is PtDefer }
                if(lastStmt != null && lastStmt !is PtReturn && lastStmt !is PtJump)
                    subEndsToAugment.add(node)
            }
            else -> {}
        }
    }

    for(exit in exitsToAugment) {
        val defer = exit.definingSub()!!.children.singleOrNull { it is PtDefer }
        if(defer != null) {
            val idx = exit.parent.children.indexOf(exit)
            val invokedefer = PtBuiltinFunctionCall("invoke_defer", true, false, DataType.UNDEFINED, exit.position)
            exit.parent.add(idx, invokedefer)
        }
    }

    for(sub in subEndsToAugment) {
        val defer = sub.children.singleOrNull { it is PtDefer }
        if(defer != null) {
            val idx = sub.children.indexOfLast { it !is PtDefer }
            val ret = PtReturn(sub.position)
            sub.add(idx+1, ret)
            val invokedefer = PtBuiltinFunctionCall("invoke_defer", true, false, DataType.UNDEFINED, sub.position)
            sub.add(idx+1, invokedefer)
        }
    }

}
