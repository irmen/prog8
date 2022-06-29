package prog8.compiler.astprocessing

import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.expressions.BinaryExpression
import prog8.ast.expressions.Expression
import prog8.ast.expressions.NumericLiteral
import prog8.ast.expressions.PrefixExpression
import prog8.ast.statements.Assignment
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.code.core.IErrorReporter
import prog8.code.core.IntegerDatatypes

internal class NotExpressionChanger(val program: Program, val errors: IErrorReporter) : AstWalker() {

    override fun before(expr: BinaryExpression, parent: Node): Iterable<IAstModification> {
        if(expr.operator=="==" || expr.operator=="!=") {
            val left = expr.left as? BinaryExpression
            if (left != null) {
                val rightValue = expr.right.constValue(program)
                if (rightValue?.number == 0.0 && rightValue.type in IntegerDatatypes) {
                    if (left.operator == "==" && expr.operator == "==") {
                        // (x==something)==0  -->  x!=something
                        left.operator = "!="
                        return listOf(IAstModification.ReplaceNode(expr, left, parent))
                    } else if (left.operator == "!=" && expr.operator == "==") {
                        // (x!=something)==0  -->  x==something
                        left.operator = "=="
                        return listOf(IAstModification.ReplaceNode(expr, left, parent))
                    } else if (left.operator == "==" && expr.operator == "!=") {
                        // (x==something)!=0 --> x==something
                        left.operator = "=="
                        return listOf(IAstModification.ReplaceNode(expr, left, parent))
                    } else if (left.operator == "!=" && expr.operator == "!=") {
                        // (x!=something)!=0 --> x!=something
                        left.operator = "!="
                        return listOf(IAstModification.ReplaceNode(expr, left, parent))
                    }
                }
            }
        }

        val left = expr.left as? BinaryExpression
        val right = expr.right as? BinaryExpression
        val leftValue = left?.right?.constValue(program)?.number
        val rightValue = right?.right?.constValue(program)?.number

        if(expr.operator == "or") {
            if(left?.operator=="==" && right?.operator=="==" && leftValue==0.0 && rightValue==0.0) {
                // (a==0) or (b==0) -> (a and b)==0
                val orExpr = BinaryExpression(left.left, "and", right.left, expr.position)
                val equalsZero = BinaryExpression(orExpr, "==", NumericLiteral.fromBoolean(false, expr.position), expr.position)
                return listOf(IAstModification.ReplaceNode(expr, equalsZero, parent))
            }
        }
        else if(expr.operator == "and") {
            if(left?.operator=="==" && right?.operator=="==" && leftValue==0.0 && rightValue==0.0) {
                // (a==0) and (b==0) -> (a or b)==0
                val orExpr = BinaryExpression(left.left, "or", right.left, expr.position)
                val equalsZero = BinaryExpression(orExpr, "==", NumericLiteral.fromBoolean(false, expr.position), expr.position)
                return listOf(IAstModification.ReplaceNode(expr, equalsZero, parent))
            }
        }
        return noModifications
    }

    override fun after(expr: BinaryExpression, parent: Node): Iterable<IAstModification> {
        // not a or not b  -> not(a and b)
        if(expr.operator=="or") {
            val left = expr.left as? PrefixExpression
            val right = expr.right as? PrefixExpression
            if(left?.operator=="not" && right?.operator=="not") {
                val andExpr = BinaryExpression(left.expression, "and", right.expression, expr.position)
                val notExpr = PrefixExpression("not", andExpr, expr.position)
                return listOf(IAstModification.ReplaceNode(expr, notExpr, parent))
            }
        }

        // not a and not b -> not(a or b)
        if(expr.operator=="and") {
            val left = expr.left as? PrefixExpression
            val right = expr.right as? PrefixExpression
            if(left?.operator=="not" && right?.operator=="not") {
                val andExpr = BinaryExpression(left.expression, "or", right.expression, expr.position)
                val notExpr = PrefixExpression("not", andExpr, expr.position)
                return listOf(IAstModification.ReplaceNode(expr, notExpr, parent))
            }
        }

        if(expr.operator=="==") {
            val rightValue = expr.right.constValue(program)
            if(rightValue?.number==0.0 && rightValue.type in IntegerDatatypes) {
                // x==0 -> not x (only if occurs as a subexpression)
                if(expr.parent is Expression || expr.parent is Assignment) {
                    val notExpr = PrefixExpression("not", expr.left.copy(), expr.position)
                    return listOf(IAstModification.ReplaceNodeSafe(expr, notExpr, parent))
                }
            }
        }

        return noModifications
    }

    override fun after(expr: PrefixExpression, parent: Node): Iterable<IAstModification> {
        if(expr.operator == "not") {
            // not(not(x)) -> x
            if((expr.expression as? PrefixExpression)?.operator=="not")
                return listOf(IAstModification.ReplaceNode(expr, expr.expression, parent))
        }
        return noModifications
    }
}
