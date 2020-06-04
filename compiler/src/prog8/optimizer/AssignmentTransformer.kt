package prog8.optimizer

import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.ErrorReporter
import prog8.ast.expressions.BinaryExpression
import prog8.ast.processing.AstWalker
import prog8.ast.processing.IAstModification
import prog8.ast.statements.Assignment
import prog8.ast.statements.PostIncrDecr



internal class AssignmentTransformer(val program: Program, val errors: ErrorReporter) : AstWalker() {

    var optimizationsDone: Int = 0

    override fun before(assignment: Assignment, parent: Node): Iterable<IAstModification> {
        // modify A = A + 5 back into augmented form A += 5 for easier code generation for optimized in-place assignments
        // also to put code generation stuff together, single value assignment (A = 5) is converted to a special
        // augmented form as wel (with the operator "setvalue")
        if (assignment.aug_op == null) {
            val binExpr = assignment.value as? BinaryExpression
            if (binExpr != null) {
                if (assignment.target.isSameAs(binExpr.left)) {
                    assignment.value = binExpr.right
                    assignment.aug_op = binExpr.operator + "="
                    assignment.value.parent = assignment
                    optimizationsDone++
                    return emptyList()
                }
            }
            assignment.aug_op = "setvalue"
            optimizationsDone++
        } else if(assignment.aug_op == "+=") {
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
        return emptyList()
    }
}
