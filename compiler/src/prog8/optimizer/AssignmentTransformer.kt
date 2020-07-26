package prog8.optimizer

import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.ErrorReporter
import prog8.ast.expressions.BinaryExpression
import prog8.ast.processing.AstWalker
import prog8.ast.processing.IAstModification
import prog8.ast.statements.Assignment
import prog8.ast.statements.PostIncrDecr

// TODO integrate this in the StatementOptimizer


internal class AssignmentTransformer(val program: Program, val errors: ErrorReporter) : AstWalker() {

    var optimizationsDone: Int = 0
    private val noModifications = emptyList<IAstModification>()

    override fun before(assignment: Assignment, parent: Node): Iterable<IAstModification> {
        if(assignment.target isSameAs assignment.value) {
            TODO("remove assignment to self")
        }

        return noModifications

        // TODO add these optimizations back:

        /*
        if(assignment.aug_op == "+=") {
            val binExpr = assignment.value as? BinaryExpression
            if (binExpr != null) {
                val leftnum = binExpr.left.constValue(program)?.number?.toDouble()
                val rightnum = binExpr.right.constValue(program)?.number?.toDouble()
                if(binExpr.operator == "+") {
                    when {
                        leftnum == 1.0 -> {
                            optimizationsDone++
                            return listOf(IAstModification.SwapOperands(binExpr))
                        }
                        leftnum == 2.0 -> {
                            optimizationsDone++
                            return listOf(IAstModification.SwapOperands(binExpr))
                        }
                        rightnum == 1.0 -> {
                            // x += y + 1  -> x += y ,  x++
                            return listOf(
                                    IAstModification.ReplaceNode(assignment.value, binExpr.left, assignment),
                                    IAstModification.InsertAfter(assignment, PostIncrDecr(assignment.target, "++", assignment.position), parent)
                            )
                        }
                        rightnum == 2.0 -> {
                            // x += y + 2  -> x += y ,  x++,  x++
                            return listOf(
                                    IAstModification.ReplaceNode(assignment.value, binExpr.left, assignment),
                                    IAstModification.InsertAfter(assignment, PostIncrDecr(assignment.target, "++", assignment.position), parent),
                                    IAstModification.InsertAfter(assignment, PostIncrDecr(assignment.target, "++", assignment.position), parent)
                            )
                        }
                    }
                } else if(binExpr.operator == "-") {
                    when {
                        leftnum == 1.0 -> {
                            optimizationsDone++
                            return listOf(IAstModification.SwapOperands(binExpr))
                        }
                        leftnum == 2.0 -> {
                            optimizationsDone++
                            return listOf(IAstModification.SwapOperands(binExpr))
                        }
                        rightnum == 1.0 -> {
                            // x += y - 1  -> x += y ,  x--
                            return listOf(
                                    IAstModification.ReplaceNode(assignment.value, binExpr.left, assignment),
                                    IAstModification.InsertAfter(assignment, PostIncrDecr(assignment.target, "--", assignment.position), parent)
                            )
                        }
                        rightnum == 2.0 -> {
                            // x += y - 2  -> x += y ,  x--,  x--
                            return listOf(
                                    IAstModification.ReplaceNode(assignment.value, binExpr.left, assignment),
                                    IAstModification.InsertAfter(assignment, PostIncrDecr(assignment.target, "--", assignment.position), parent),
                                    IAstModification.InsertAfter(assignment, PostIncrDecr(assignment.target, "--", assignment.position), parent)
                            )
                        }
                    }
                }
            }
        } else if(assignment.aug_op == "-=") {
            val binExpr = assignment.value as? BinaryExpression
            if (binExpr != null) {
                val leftnum = binExpr.left.constValue(program)?.number?.toDouble()
                val rightnum = binExpr.right.constValue(program)?.number?.toDouble()
                if(binExpr.operator == "+") {
                    when {
                        leftnum == 1.0 -> {
                            optimizationsDone++
                            return listOf(IAstModification.SwapOperands(binExpr))
                        }
                        leftnum == 2.0 -> {
                            optimizationsDone++
                            return listOf(IAstModification.SwapOperands(binExpr))
                        }
                        rightnum == 1.0 -> {
                            // x -= y + 1  -> x -= y ,  x--
                            return listOf(
                                    IAstModification.ReplaceNode(assignment.value, binExpr.left, assignment),
                                    IAstModification.InsertAfter(assignment, PostIncrDecr(assignment.target, "--", assignment.position), parent)
                            )
                        }
                        rightnum == 2.0 -> {
                            // x -= y + 2  -> x -= y ,  x--,  x--
                            return listOf(
                                    IAstModification.ReplaceNode(assignment.value, binExpr.left, assignment),
                                    IAstModification.InsertAfter(assignment, PostIncrDecr(assignment.target, "--", assignment.position), parent),
                                    IAstModification.InsertAfter(assignment, PostIncrDecr(assignment.target, "--", assignment.position), parent)
                            )
                        }
                    }
                } else if(binExpr.operator == "-") {
                    when {
                        leftnum == 1.0 -> {
                            optimizationsDone++
                            return listOf(IAstModification.SwapOperands(binExpr))
                        }
                        leftnum == 2.0 -> {
                            optimizationsDone++
                            return listOf(IAstModification.SwapOperands(binExpr))
                        }
                        rightnum == 1.0 -> {
                            // x -= y - 1  -> x -= y ,  x++
                            return listOf(
                                    IAstModification.ReplaceNode(assignment.value, binExpr.left, assignment),
                                    IAstModification.InsertAfter(assignment, PostIncrDecr(assignment.target, "++", assignment.position), parent)
                            )
                        }
                        rightnum == 2.0 -> {
                            // x -= y - 2  -> x -= y ,  x++,  x++
                            return listOf(
                                    IAstModification.ReplaceNode(assignment.value, binExpr.left, assignment),
                                    IAstModification.InsertAfter(assignment, PostIncrDecr(assignment.target, "++", assignment.position), parent),
                                    IAstModification.InsertAfter(assignment, PostIncrDecr(assignment.target, "++", assignment.position), parent)
                            )
                        }
                    }
                }
            }
        }
        return noModifications
        */
    }
}
