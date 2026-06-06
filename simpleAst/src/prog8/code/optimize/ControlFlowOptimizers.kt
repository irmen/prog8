package prog8.code.optimize

import prog8.code.ast.*
import prog8.code.core.DataType
import prog8.code.core.IErrorReporter

/**
 * Control flow optimizations.
 * Handles if/else, when statements, and conditional branches.
 */
internal object ControlFlowOptimizers {

    /**
     * Optimizes conditional expressions with constant conditions.
     * if true {A} else {B} -> A
     * if false {A} else {B} -> B
     */
    fun optimizeConditionalExpressions(program: PtProgram, errors: IErrorReporter): Int {
        val toOptimize = mutableListOf<Triple<PtIfElse, Boolean, PtNode>>()

        walkAst(program) { node: PtNode, depth: Int ->
            if (node is PtIfElse) {
                val condition = node.condition
                val trueConst = condition.asConstValue()

                // if true { A } else { B } -> A
                if (trueConst == 1.0 || (condition is PtBool && condition.value)) {
                    toOptimize.add(Triple(node, true, node.parent))
                }
                // if false { A } else { B } -> B
                else if (trueConst == 0.0 || (condition is PtBool && !condition.value)) {
                    toOptimize.add(Triple(node, false, node.parent))
                }
            }
            true
        }

        var changes = 0
        for ((node, takeTrue, parent) in toOptimize) {
            val index = parent.children.indexOf(node)
            if (index == -1) continue

            parent.removeChildAt(index)
            val scope = if (takeTrue) node.ifScope else node.elseScope
            // Insert the scope statements in reverse order to maintain order
            scope.children.reversed().forEach { stmt ->
                parent.add(index, stmt)
                stmt.parent = parent
            }
            changes++
        }
        return changes
    }

    /**
     * Optimizes dead conditional branches in when statements.
     */
    fun optimizeDeadConditionalBranches(program: PtProgram): Int {
        val toOptimize = mutableListOf<PtWhen>()

        walkAst(program) { node: PtNode, depth: Int ->
            if (node is PtWhen) {
                val choices = node.choices.children
                // If we have a when with only an else choice, convert to if-else or just the else block
                if (choices.size == 1 && choices[0] is PtWhenChoice && (choices[0] as PtWhenChoice).isElse) {
                    toOptimize.add(node)
                }
            }
            true
        }

        var changes = 0
        for (node in toOptimize) {
            if (!node.parent.children.contains(node)) continue
            
            val elseChoice = node.choices.children[0] as PtWhenChoice
            val index = node.parent.children.indexOf(node)
            val parent = node.parent
            parent.removeChildAt(index)
            // Insert the else part statements
            elseChoice.statements.children.reversed().forEach { stmt ->
                parent.add(index, stmt)
                stmt.parent = parent
            }
            changes++
        }
        return changes
    }

    /**
     * Simplifies single-choice when statements to if-else.
     */
    fun optimizeSingleWhens(program: PtProgram, errors: IErrorReporter): Int {
        val toOptimize = mutableListOf<PtWhen>()

        walkAst(program) { node: PtNode, depth: Int ->
            if(node is PtWhen && node.choices.children.size==2) {
                val choice1 = node.choices.children[0] as PtWhenChoice
                val choice2 = node.choices.children[1] as PtWhenChoice
                if(choice1.isElse && choice2.values.children.size==1 || choice2.isElse && choice1.values.children.size==1) {
                    toOptimize.add(node)
                }
            }
            true
        }

        var changes = 0
        for (node in toOptimize) {
            if (!node.parent.children.contains(node)) continue
            
            val choice1 = node.choices.children[0] as PtWhenChoice
            val choice2 = node.choices.children[1] as PtWhenChoice
            
            errors.info("when can be simplified into an if-else", node.position)
            val truescope: PtNodeGroup
            val elsescope: PtNodeGroup
            val comparisonValue: PtNumber
            if (choice1.isElse) {
                truescope = choice2.statements
                elsescope = choice1.statements
                comparisonValue = choice2.values.children.single() as PtNumber
            } else {
                truescope = choice1.statements
                elsescope = choice2.statements
                comparisonValue = choice1.values.children.single() as PtNumber
            }
            val ifelse = PtIfElse(node.position)
            val condition = PtBinaryExpression("==", DataType.BOOL, node.position)
            condition.add(node.value)
            condition.add(comparisonValue)
            ifelse.add(condition)
            ifelse.add(truescope)
            ifelse.add(elsescope)
            ifelse.parent = node.parent
            val index = node.parent.children.indexOf(node)
            node.parent.setChild(index, ifelse)
            changes++
        }
        return changes
    }
}
