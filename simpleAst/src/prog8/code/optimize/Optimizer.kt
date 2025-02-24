package prog8.code.optimize

import prog8.code.StExtSub
import prog8.code.SymbolTable
import prog8.code.ast.*
import prog8.code.core.*
import prog8.code.target.VMTarget


fun optimizeSimplifiedAst(program: PtProgram, options: CompilationOptions, st: SymbolTable, errors: IErrorReporter) {
    if (!options.optimize)
        return
    while (errors.noErrors() &&
        optimizeAssignTargets(program, st) + optimizeWordPlusTimesTwo(program, options) > 0) {
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


private fun optimizeAssignTargets(program: PtProgram, st: SymbolTable): Int {
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


private fun optimizeWordPlusTimesTwo(program: PtProgram, options: CompilationOptions): Int {
    if(options.compTarget.name== VMTarget.NAME)
        return 0
    var changes = 0
    walkAst(program) { node: PtNode, depth: Int ->
        if (node is PtBinaryExpression) {
            if(node.operator=="*" && node.right.type.isWord && node.right.asConstValue()==2.0) {
                TODO("optimize word + byte*2 (usually already replaced by w+b<<2)")
            }
            else if(node.operator=="<<" && node.right.asConstValue()==1.0) {
                val typecast=node.left as? PtTypeCast
                if(typecast!=null && typecast.type.isWord && typecast.value is PtIdentifier) {
                    val addition = node.parent as? PtBinaryExpression
                    if(addition!=null && (addition.operator=="+" || addition.operator=="-") && addition.type.isWord) {
                        // word + (byte<<1 as uword) (== word + byte*2)  -->  (word + (byte as word)) + (byte as word)
                        val parent = addition.parent
                        val index = parent.children.indexOf(addition)
                        val addFirst = PtBinaryExpression(addition.operator, addition.type, addition.position)
                        val addSecond = PtBinaryExpression(addition.operator, addition.type, addition.position)
                        if(addition.left===node)
                            addFirst.add(addition.right)
                        else
                            addFirst.add(addition.left)
                        addFirst.add(typecast)
                        addSecond.add(addFirst)
                        addSecond.add(typecast.copy())
                        parent.children[index] = addSecond
                        addSecond.parent = parent
                        changes++
                    }
                }
            }
        }
        true
    }
    return changes
}
