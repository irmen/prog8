package prog8.code.optimize

import prog8.code.ast.*
import prog8.code.core.*


fun optimizeIntermediateAst(program: PtProgram, options: CompilationOptions, errors: IErrorReporter) {
    if (!options.optimize)
        return
    while(errors.noErrors() && optimizeCommonSubExpressions(program, errors)>0) {
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


private fun optimizeCommonSubExpressions(program: PtProgram, errors: IErrorReporter): Int {

    fun extractableSubExpr(expr: PtExpression): Boolean {
        return if(expr is PtBinaryExpression)
            !expr.left.isSimple() || !expr.right.isSimple() || (expr.operator !in LogicalOperators && expr.operator !in BitwiseOperators)
        else
            !expr.isSimple()
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
        val tempvarName = "subexprvar_line${binexpr.position.line}_${binexpr.hashCode().toUInt()}"
        // TODO: some tempvars could be reused, if they are from different lines

        val datatype = occurrence1.type
        val singleReplacement1 = PtIdentifier("$containerScopedName.$tempvarName", datatype, occurrence1.position)
        val singleReplacement2 = PtIdentifier("$containerScopedName.$tempvarName", datatype, occurrence2.position)
        occurrence1.parent.children[occurrence1idx] = singleReplacement1
        singleReplacement1.parent = occurrence1.parent
        occurrence2.parent.children[occurrence2idx] = singleReplacement2
        singleReplacement2.parent = occurrence2.parent

        val tempassign = PtAssignment(binexpr.position).also { assign ->
            assign.add(PtAssignTarget(binexpr.position).also { tgt->
                tgt.add(PtIdentifier("$containerScopedName.$tempvarName", datatype, binexpr.position))
            })
            assign.add(occurrence1)
            occurrence1.parent = assign
        }
        stmtContainer.children.add(stmtContainer.children.indexOf(stmt), tempassign)
        tempassign.parent = stmtContainer

        val tempvar = PtVariable(tempvarName, datatype, ZeropageWish.DONTCARE, null, null, binexpr.position)
        stmtContainer.add(0, tempvar)
        tempvar.parent = stmtContainer

        // errors.info("common subexpressions replaced by a tempvar, maybe simplify the expression manually", binexpr.position)
    }

    return commons.size
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
