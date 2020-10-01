package prog8.optimizer

import prog8.ast.INameScope
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.expressions.*
import prog8.ast.processing.AstWalker
import prog8.ast.processing.IAstModification
import prog8.ast.statements.AssignTarget
import prog8.ast.statements.Assignment

class ExpressionSplitter(private val program: Program) : AstWalker() {

    // TODO once this works, integrate it back into expressionsimplifier
    override fun after(assignment: Assignment, parent: Node): Iterable<IAstModification> {

        if(assignment!=null) {
            val expr = assignment.value as? BinaryExpression
            if (expr != null) {
                // reduce the complexity of a (binary) expression that has to be evaluated on the eval stack,
                // by attempting to splitting it up into individual simple steps:
                // X = <some-expression-not-X> <operator> <not-binary-expression>
                // or X = <not-binary-expression> <associativeoperator> <some-expression-not-X>
                //     split that into  X = <some-expression-not-X> ;  X = X <operator> <not-binary-expression>

                // TODO FIX THIS, IT SOMETIMES JUST LOOPS... (for example on plasma.p8)
                if (expr.operator !in comparisonOperators && !assignment.isAugmentable && isSimpleTarget(assignment.target, program.namespace)) {
                    if (expr.right !is BinaryExpression) {
                        println("SPLIT RIGHT BINEXPR $expr")
                        val firstAssign = Assignment(assignment.target, expr.left, assignment.position)
                        val augExpr = BinaryExpression(assignment.target.toExpression(), expr.operator, expr.right, expr.position)
                        return listOf(
                                IAstModification.InsertBefore(assignment, firstAssign, parent),
                                IAstModification.ReplaceNode(assignment.value, augExpr, assignment)
                        )
                    } else if (expr.left !is BinaryExpression && expr.operator in associativeOperators) {
                        println("SPLIT LEFT BINEXPR $expr")
                        val firstAssign = Assignment(assignment.target, expr.right, assignment.position)
                        val augExpr = BinaryExpression(assignment.target.toExpression(), expr.operator, expr.left, expr.position)
                        return listOf(
                                IAstModification.InsertBefore(assignment, firstAssign, parent),
                                IAstModification.ReplaceNode(assignment.value, augExpr, assignment))
                    }
                }
            }
        }

        return emptyList()
    }

    private fun isSimpleTarget(target: AssignTarget, namespace: INameScope): Boolean {
        return when {
            target.identifier!=null -> target.isInRegularRAM(namespace)
            target.memoryAddress!=null -> target.isInRegularRAM(namespace)
            target.arrayindexed!=null -> {
                val index = target.arrayindexed!!.arrayspec.index
                if(index is NumericLiteralValue || index is IdentifierReference)
                    target.isInRegularRAM(namespace)
                else
                    false
            }
            else -> false
        }
    }
}
