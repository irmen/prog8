package prog8.code.optimize

import prog8.code.StExtSub
import prog8.code.SymbolTable
import prog8.code.ast.*
import prog8.code.core.*


fun optimizeIntermediateAst(program: PtProgram, options: CompilationOptions, st: SymbolTable, errors: IErrorReporter) {
    if (!options.optimize)
        return
    while (errors.noErrors() &&
            (optimizeBitTest(program, options)
            + optimizeAssignTargets(program, st, errors)) > 0
    ) {
        // keep rolling
    }
}


private fun walkAst(root: PtNode, act: (node: PtNode, depth: Int) -> Boolean) {
    fun recurse(node: PtNode, depth: Int) {
        if(act(node, depth))
            node.children.forEach { recurse(it, depth+1) }
    }
    recurse(root, 0)
}


private fun optimizeAssignTargets(program: PtProgram, st: SymbolTable, errors: IErrorReporter): Int {
    var changes = 0
    walkAst(program) { node: PtNode, depth: Int ->
        if(node is PtAssignment) {
            val value = node.value
            val functionName = when(value) {
                is PtBuiltinFunctionCall -> value.name
                is PtFunctionCall -> value.name
                else -> null
            }
            if(functionName!=null) {
                val stNode = st.lookup(functionName)
                if (stNode is StExtSub) {
                    require(node.children.size==stNode.returns.size+1) {
                        "number of targets must match return values"
                    }
                    node.children.zip(stNode.returns).withIndex().forEach { (index, xx) ->
                        val target = xx.first as PtAssignTarget
                        val returnedRegister = xx.second.register.registerOrPair
                        if(returnedRegister!=null && !target.void && target.identifier!=null) {
                            if(isSame(target.identifier!!, xx.second.type, returnedRegister)) {
                                // output register is already identical to target register, so it can become void
                                val voidTarget = PtAssignTarget(true, target.position)
                                node.children[index] = voidTarget
                                voidTarget.parent = node
                                changes++
                            }
                        }
                    }
                }
                if(node.children.dropLast(1).all { (it as PtAssignTarget).void }) {
                    // all targets are now void, the whole assignment can be discarded and replaced by just a (void) call to the subroutine
                    val index = node.parent.children.indexOf(node)
                    val voidCall = PtFunctionCall(functionName, true, DataType.forDt(BaseDataType.UNDEFINED), value.position)
                    value.children.forEach { voidCall.add(it) }
                    node.parent.children[index] = voidCall
                    voidCall.parent = node.parent
                    changes++
                }
            }
        }
        true
    }
    return changes
}


private fun optimizeBitTest(program: PtProgram, options: CompilationOptions): Int {
    if(options.compTarget.machine.cpu == CpuType.VIRTUAL)
        return 0        // the special bittest optimization is not yet valid for the IR

    fun makeBittestCall(condition: PtBinaryExpression, and: PtBinaryExpression, variable: PtIdentifier, bitmask: Int): PtBuiltinFunctionCall {
        require(bitmask==128 || bitmask==64)
        val setOrNot = if(condition.operator=="!=") "set" else "notset"
        val bittestCall = PtBuiltinFunctionCall("prog8_ifelse_bittest_$setOrNot", false, true, DataType.forDt(BaseDataType.BOOL), condition.position)
        bittestCall.add(variable)
        if(bitmask==128)
            bittestCall.add(PtNumber(BaseDataType.UBYTE, 7.0, and.right.position))
        else
            bittestCall.add(PtNumber(BaseDataType.UBYTE, 6.0, and.right.position))
        return bittestCall
    }

    fun isAndByteCondition(condition: PtBinaryExpression?): Triple<PtBinaryExpression, PtIdentifier, Int>? {
        if(condition!=null && (condition.operator=="==" || condition.operator=="!=")) {
            if (condition.right.asConstInteger() == 0) {
                val and = condition.left as? PtBinaryExpression
                if (and != null && and.operator == "&" && and.type.isUnsignedByte) {
                    val bitmask = and.right.asConstInteger()
                    if(bitmask==128 || bitmask==64) {
                        val variable = and.left as? PtIdentifier
                        if (variable != null && variable.type.isByte) {
                            return Triple(and, variable, bitmask)
                        }
                        val typecast = and.left as? PtTypeCast
                        if (typecast != null && typecast.type.isUnsignedByte) {
                            val castedVariable = typecast.value as? PtIdentifier
                            if(castedVariable!=null && castedVariable.type.isByte)
                                return Triple(and, castedVariable, bitmask)
                        }
                    }
                }
            }
        }
        return null
    }

    var changes = 0
    var recurse = true
    walkAst(program) { node: PtNode, depth: Int ->
        if(node is PtIfElse) {
            val condition = node.condition as? PtBinaryExpression
            val check = isAndByteCondition(condition)
            if(check!=null) {
                val (and, variable, bitmask) = check
                val bittestCall = makeBittestCall(condition!!, and, variable, bitmask)
                val ifElse = PtIfElse(node.position)
                ifElse.add(bittestCall)
                ifElse.add(node.ifScope)
                if (node.hasElse())
                    ifElse.add(node.elseScope)
                val index = node.parent.children.indexOf(node)
                node.parent.children[index] = ifElse
                ifElse.parent = node.parent
                changes++
                recurse = false
            }
        }
        if (node is PtIfExpression) {
            val condition = node.condition as? PtBinaryExpression
            val check = isAndByteCondition(condition)
            if(check!=null) {
                val (and, variable, bitmask) = check
                val bittestCall = makeBittestCall(condition!!, and, variable, bitmask)
                node.children[0] = bittestCall
                bittestCall.parent = node
                changes++
                recurse = false
            }
        }
        recurse
    }
    return changes
}


internal fun isSame(identifier: PtIdentifier, type: DataType, returnedRegister: RegisterOrPair): Boolean {
    if(returnedRegister in Cx16VirtualRegisters) {
        val regname = returnedRegister.name.lowercase()
        val identifierRegName = identifier.name.substringAfterLast('.')
        /*
            cx16.r?    UWORD
            cx16.r?s   WORD
            cx16.r?L   UBYTE
            cx16.r?H   UBYTE
            cx16.r?sL  BYTE
            cx16.r?sH  BYTE
         */
        if(identifier.type.isByte && type.isByte) {
            if(identifier.name.startsWith("cx16.$regname") && identifierRegName.startsWith(regname)) {
                return identifierRegName.substring(2) in arrayOf("", "L", "sL")     // note: not the -H (msb) variants!
            }
        }
        else if(identifier.type.isWord && type.isWord) {
            if(identifier.name.startsWith("cx16.$regname") && identifierRegName.startsWith(regname)) {
                return identifierRegName.substring(2) in arrayOf("", "s")
            }
        }
    }
    return false   // there are no identifiers directly corresponding to cpu registers
}
