package prog8.code.optimize

import prog8.code.StExtSub
import prog8.code.SymbolTable
import prog8.code.ast.*
import prog8.code.core.*
import prog8.code.target.VMTarget
import kotlin.math.log2


fun optimizeSimplifiedAst(program: PtProgram, options: CompilationOptions, st: SymbolTable, errors: IErrorReporter) {
    if (!options.optimize)
        return
    while (errors.noErrors() &&
        optimizeAssignTargets(program, st)
        + optimizeFloatComparesToZero(program)
        + optimizeBinaryExpressions(program, options) > 0) {
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
                    val voidCall = PtFunctionCall(functionName, true, DataType.UNDEFINED, value.position)
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


private fun optimizeBinaryExpressions(program: PtProgram, options: CompilationOptions): Int {
    var changes = 0
    walkAst(program) { node: PtNode, depth: Int ->
        if (node is PtBinaryExpression) {
            val constvalue = node.right.asConstValue()
            if(node.operator=="<<" && constvalue==1.0 && options.compTarget.name!=VMTarget.NAME) {
                val typecast=node.left as? PtTypeCast
                if(typecast!=null && typecast.type.isWord && typecast.value is PtIdentifier) {
                    val addition = node.parent as? PtBinaryExpression
                    if(addition!=null && addition.operator=="+" && addition.type.isWord) {
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
            else if (node.operator=="*" && !node.right.type.isFloat) {
                if (constvalue in powersOfTwoFloat) {
                    // x * power-of-two -> bitshift
                    val numshifts = log2(constvalue!!)
                    val shift = PtBinaryExpression("<<", node.type, node.position)
                    shift.add(node.left)
                    shift.add(PtNumber(BaseDataType.UBYTE, numshifts, node.position))
                    shift.parent = node.parent
                    val index = node.parent.children.indexOf(node)
                    node.parent.children[index] = shift
                    changes++
                } else if(constvalue in negativePowersOfTwoFloat) {
                    TODO("x * negative power-of-two -> bitshift  ${node.position}")
                }
            } else if(node.operator=="*" && !node.right.type.isFloat && constvalue in negativePowersOfTwoFloat) {
                TODO("x * negative power-of-two -> bitshift  ${node.position}")
            }
        }
        true
    }
    return changes
}


private fun optimizeFloatComparesToZero(program: PtProgram): Int {
    var changes = 0
    walkAst(program) { node: PtNode, depth: Int ->
        if (node is PtBinaryExpression) {
            val constvalue = node.right.asConstValue()
            if(node.type.isBool && constvalue==0.0 && node.left.type.isFloat && node.operator in ComparisonOperators) {
                // float == 0 --> sgn(float) == 0
                val sign = PtBuiltinFunctionCall("sgn", false, true, DataType.BYTE, node.position)
                sign.add(node.left)
                val replacement = PtBinaryExpression(node.operator, DataType.BOOL, node.position)
                replacement.add(sign)
                replacement.add(PtNumber(BaseDataType.BYTE, 0.0, node.position))
                replacement.parent = node.parent
                val index = node.parent.children.indexOf(node)
                node.parent.children[index] = replacement
                changes++
            }
        }
        true
    }
    return changes
}