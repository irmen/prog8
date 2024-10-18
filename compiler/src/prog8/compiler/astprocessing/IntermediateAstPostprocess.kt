package prog8.compiler.astprocessing

import prog8.ast.base.FatalAstException
import prog8.code.SymbolTable
import prog8.code.ast.*
import prog8.code.core.*

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
    val jumpsToAugment = mutableListOf<PtJump>()
    val returnsToAugment = mutableListOf<PtReturn>()
    val subEndsToAugment = mutableListOf<PtSub>()

    walkAst(program) { node, _ ->
        when(node) {
            is PtJump -> {
                if(node.identifier!=null) {
                    val stNode = st.lookup(node.identifier!!.name)!!
                    val targetSub = stNode.astNode.definingSub()
                    if(targetSub!=node.definingSub())
                        jumpsToAugment.add(node)
                }
            }
            is PtReturn -> returnsToAugment.add(node)
            is PtSub -> {
                val lastStmt = node.children.lastOrNull { it !is PtDefer }
                if(lastStmt != null && lastStmt !is PtReturn && lastStmt !is PtJump)
                    subEndsToAugment.add(node)
            }
            else -> {}
        }
    }

    fun invokedeferbefore(node: PtNode) {
        val defer = node.definingSub()!!.children.singleOrNull { it is PtDefer }
        if (defer != null) {
            val idx = node.parent.children.indexOf(node)
            val invokedefer = PtBuiltinFunctionCall("invoke_defer", true, false, DataType.UNDEFINED, node.position)
            node.parent.add(idx, invokedefer)
        }
    }

    for(exit in jumpsToAugment) {
        invokedeferbefore(exit)
    }

    for(ret in returnsToAugment) {
        val defer = ret.definingSub()!!.children.singleOrNull { it is PtDefer }
        if(defer == null)
            continue
        if(ret.children.size>1)
            TODO("support defer on multi return values")
        if(!ret.hasValue || ret.value!!.isSimple()) {
            invokedeferbefore(ret)
        } else {
            val value = ret.value!!
            var typecast: DataType? = null
            var pushWord = false
            var pushFloat = false

            when(value.type) {
                DataType.BOOL -> typecast = DataType.BOOL
                DataType.BYTE -> typecast = DataType.BYTE
                DataType.WORD -> {
                    pushWord = true
                    typecast = DataType.WORD
                }
                DataType.UBYTE -> {}
                DataType.UWORD, in PassByReferenceDatatypes -> pushWord = true
                DataType.FLOAT -> pushFloat = true
                else -> throw FatalAstException("unsupported return value type ${value.type} with defer")
            }

            val pushFunc = if(pushFloat) "floats.push" else if(pushWord) "sys.pushw" else "sys.push"
            val popFunc = if(pushFloat) "floats.pop" else if(pushWord) "sys.popw" else "sys.pop"
            val pushCall = PtFunctionCall(pushFunc, true, value.type, value.position)
            pushCall.add(value)
            val popCall = if(typecast!=null) {
                PtTypeCast(typecast, value.position).also {
                    it.add(PtFunctionCall(popFunc, false, value.type, value.position))
                }
            } else
                PtFunctionCall(popFunc, false, value.type, value.position)

            val newRet = PtReturn(ret.position)
            newRet.add(popCall)
            val group = PtNodeGroup()
            group.add(pushCall)
            group.add(PtBuiltinFunctionCall("invoke_defer", true, false, DataType.UNDEFINED, ret.position))
            group.add(newRet)
            group.parent = ret.parent
            val idx = ret.parent.children.indexOf(ret)
            ret.parent.children[idx] = group
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
