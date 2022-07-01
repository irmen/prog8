package prog8.compiler.astprocessing

import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.FatalAstException
import prog8.ast.expressions.BinaryExpression
import prog8.ast.expressions.NumericLiteral
import prog8.ast.expressions.PrefixExpression
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

        return noModifications
    }

    override fun after(expr: PrefixExpression, parent: Node): Iterable<IAstModification> {
        if(expr.operator == "not") {
            // not(not(x)) -> x
            if((expr.expression as? PrefixExpression)?.operator=="not")
                return listOf(IAstModification.ReplaceNode(expr, expr.expression, parent))
            // not(~x) -> x!=0
            if((expr.expression as? PrefixExpression)?.operator=="~") {
                val x = (expr.expression as PrefixExpression).expression
                val dt = x.inferType(program).getOrElse { throw FatalAstException("invalid dt") }
                val notZero = BinaryExpression(x, "!=", NumericLiteral(dt, 0.0, expr.position), expr.position)
                return listOf(IAstModification.ReplaceNode(expr, notZero, parent))
            }
            val subBinExpr = expr.expression as? BinaryExpression
            if(subBinExpr?.operator=="==") {
                if(subBinExpr.right.constValue(program)?.number==0.0) {
                    // not(x==0) -> x!=0
                    subBinExpr.operator = "!="
                    return listOf(IAstModification.ReplaceNode(expr, subBinExpr, parent))
                }
            } else if(subBinExpr?.operator=="!=") {
                if(subBinExpr.right.constValue(program)?.number==0.0) {
                    // not(x!=0) -> x==0
                    subBinExpr.operator = "=="
                    return listOf(IAstModification.ReplaceNode(expr, subBinExpr, parent))
                }
            }
        }
        return noModifications
    }
}
