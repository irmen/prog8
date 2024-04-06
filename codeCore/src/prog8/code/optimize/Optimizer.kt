package prog8.code.optimize

import prog8.code.StRomSub
import prog8.code.SymbolTable
import prog8.code.ast.*
import prog8.code.core.*


fun optimizeIntermediateAst(program: PtProgram, options: CompilationOptions, st: SymbolTable, errors: IErrorReporter) {
    if (!options.optimize)
        return
    while (errors.noErrors() &&
        (optimizeCommonSubExpressions(program, errors)
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


private var tempVarCounter = 0

private fun optimizeCommonSubExpressions(program: PtProgram, errors: IErrorReporter): Int {

    fun extractableSubExpr(expr: PtExpression): Boolean {
        if(expr is PtArrayIndexer && expr.index.isSimple())
            return false
        if (expr is PtMemoryByte && expr.address.isSimple())
            return false

        val result = if(expr is PtBinaryExpression)
            expr.type !in ByteDatatypes ||
            !(expr.left.isSimple() && expr.right.isSimple()) ||
            (expr.operator !in LogicalOperators && expr.operator !in BitwiseOperators)
        else if (expr is PtArrayIndexer && expr.type !in ByteDatatypes)
            true
        else
            !expr.isSimple()
        return result
    }

    // for each Binaryexpression, recurse to find a common subexpression pair therein.
    val commons = mutableMapOf<PtBinaryExpression, Pair<PtExpression, PtExpression>>()
    walkAst(program) { node: PtNode, depth: Int ->
        if(node is PtBinaryExpression) {
            val subExpressions = mutableListOf<PtExpression>()
            walkAst(node.left) { subNode: PtNode, subDepth: Int ->
                if (subNode is PtExpression) {
                    if(extractableSubExpr(subNode)) subExpressions.add(subNode)
                    true
                } else false
            }
            walkAst(node.right) { subNode: PtNode, subDepth: Int ->
                if (subNode is PtExpression) {
                    if(extractableSubExpr(subNode)) subExpressions.add(subNode)
                    true
                } else false
            }

            outer@for (first in subExpressions) {
                for (second in subExpressions) {
                    if (first!==second && first isSameAs second) {
                        commons[node] = first to second
                        break@outer     // do only 1 replacement at a time per binaryexpression
                    }
                }
            }
            false
        } else true
    }

    // replace common subexpressions by a temp variable that is assigned only once.
    // TODO: check for commonalities across multiple separate expressions in the current scope, not only inside a single line
    commons.forEach { binexpr, (occurrence1, occurrence2) ->
        val (stmtContainer, stmt) = findContainingStatements(binexpr)
        val occurrence1idx = occurrence1.parent.children.indexOf(occurrence1)
        val occurrence2idx = occurrence2.parent.children.indexOf(occurrence2)
        val containerScopedName = findScopeName(stmtContainer)
        tempVarCounter++
        val tempvarName = "prog8_subexprvar_$tempVarCounter"
        // TODO: some tempvars could be reused, if they are from different lines

        val datatype = occurrence1.type
        val singleReplacement1 = PtIdentifier("$containerScopedName.$tempvarName", datatype, occurrence1.position)
        val singleReplacement2 = PtIdentifier("$containerScopedName.$tempvarName", datatype, occurrence2.position)
        occurrence1.parent.children[occurrence1idx] = singleReplacement1
        singleReplacement1.parent = occurrence1.parent
        occurrence2.parent.children[occurrence2idx] = singleReplacement2
        singleReplacement2.parent = occurrence2.parent

        val tempassign = PtAssignment(binexpr.position).also { assign ->
            assign.add(PtAssignTarget(false, binexpr.position).also { tgt->
                tgt.add(PtIdentifier("$containerScopedName.$tempvarName", datatype, binexpr.position))
            })
            assign.add(occurrence1)
            occurrence1.parent = assign
        }
        stmtContainer.children.add(stmtContainer.children.indexOf(stmt), tempassign)
        tempassign.parent = stmtContainer

        val tempvar = PtVariable(tempvarName, datatype, ZeropageWish.NOT_IN_ZEROPAGE, null, null, binexpr.position)
        stmtContainer.add(0, tempvar)
        tempvar.parent = stmtContainer

        // errors.info("common subexpressions replaced by a tempvar, maybe simplify the expression manually", binexpr.position)
    }

    return commons.size
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
                if (stNode is StRomSub) {
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
                    val voidCall = PtFunctionCall(functionName, true, value.type, value.position)
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
        if(identifier.type in ByteDatatypes && type in ByteDatatypes) {
            if(identifier.name.startsWith("cx16.$regname") && identifierRegName.startsWith(regname)) {
                return identifierRegName.substring(2) in arrayOf("", "L", "sL")     // note: not the -H (msb) variants!
            }
        }
        else if(identifier.type in WordDatatypes && type in WordDatatypes) {
            if(identifier.name.startsWith("cx16.$regname") && identifierRegName.startsWith(regname)) {
                return identifierRegName.substring(2) in arrayOf("", "s")
            }
        }
    }
    return false   // there are no identifiers directly corresponding to cpu registers
}


internal fun findScopeName(node: PtNode): String {
    var parent=node
    while(parent !is PtNamedNode)
        parent = parent.parent
    return parent.scopedName
}


internal fun findContainingStatements(node: PtNode): Pair<PtNode, PtNode> {      // returns (parentstatementcontainer, childstatement)
    var parent = node.parent
    var child = node
    while(true) {
        if(parent is IPtStatementContainer) {
            return parent to child
        }
        child=parent
        parent=parent.parent
    }
}
