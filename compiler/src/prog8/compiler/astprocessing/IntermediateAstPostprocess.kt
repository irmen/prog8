package prog8.compiler.astprocessing

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
    val callsToAugment = mutableListOf<PtFunctionCall>()

    walkAst(program) { node, _ ->
        when(node) {
            is PtFunctionCall -> {
                if(node.name.startsWith("sys.exit"))
                    callsToAugment.add(node)
            }
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
    fun notComplex(value: PtExpression): Boolean = when(value) {
        is PtAddressOf -> value.arrayIndexExpr == null || notComplex(value.arrayIndexExpr!!)
        is PtBuiltinFunctionCall -> {
            when (value.name) {
                in arrayOf("msb", "lsb", "mkword", "set_carry", "set_irqd", "clear_carry", "clear_irqd") -> value.args.all { notComplex(it) }
                else -> false
            }
        }
        is PtMemoryByte -> value.address is PtNumber
        is PtPrefix -> notComplex(value.value)
        is PtTypeCast -> notComplex(value.value)
        is PtArray,
        is PtIrRegister,
        is PtBool,
        is PtNumber,
        is PtRange,
        is PtString -> true
        else -> false
    }

    // calls (sys.exit) exits
    for(call in callsToAugment) {
        invokedeferbefore(call)
    }

    // jump exits
    for(exit in jumpsToAugment) {
        invokedeferbefore(exit)
    }

    // return exits
    for(ret in returnsToAugment) {
        val defer = ret.definingSub()!!.children.singleOrNull { it is PtDefer }
        if(defer == null)
            continue
        val value = ret.value
        if(value==null || notComplex(value)) {
            invokedeferbefore(ret)
            continue
        }

        // complex return value, need to store it before calling the defer block
        val (pushCall, popCall) = makePushPopFunctionCalls(value)
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

    // subroutine ends
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


/*  start of new defer implementation:

private fun integrateDefers(program: PtProgram, errors: IErrorReporter) {
    val defersPerSub = mutableMapOf<PtSub, MutableList<PtDefer>>().withDefault { mutableListOf() }

    walkAst(program) { node, _ ->
        if(node is PtDefer) {
            val scope = node.definingSub()!!
            val defers = defersPerSub.getValue(scope)
            defers.add(node)
            defersPerSub[scope] = defers
        }
    }

    val maskVarName = "prog8_defers_mask"

    for((sub, defers) in defersPerSub) {

        if(defers.isEmpty())
            continue
        if (defers.size > 8) {
            errors.err("can have no more than 8 defers per subroutine", sub.position)
            return
        }

        val deferVariable = PtVariable(maskVarName, DataType.UBYTE, ZeropageWish.NOT_IN_ZEROPAGE, null, null, sub.position)
        sub.add(0, deferVariable)

        for((deferIndex, defer) in defers.withIndex()) {
            val idx = defer.parent.children.indexOf(defer)
            val enableDefer = PtAugmentedAssign("|=", defer.position)
            val target = PtAssignTarget(true, defer.position)
            target.add(PtIdentifier(sub.scopedName+"."+maskVarName, DataType.UBYTE, defer.position))
            enableDefer.add(target)
            enableDefer.add(PtNumber(DataType.UBYTE, (1 shl deferIndex).toDouble(), defer.position))
            sub.add(idx, enableDefer)
        }
    }
}


 */