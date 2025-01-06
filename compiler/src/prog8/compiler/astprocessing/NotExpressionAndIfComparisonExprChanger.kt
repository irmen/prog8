package prog8.compiler.astprocessing

import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.expressions.*
import prog8.ast.statements.IfElse
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.code.core.ICompilationTarget
import prog8.code.core.IErrorReporter
import prog8.code.core.invertedComparisonOperator
import prog8.code.core.isIntegerOrBool

internal class NotExpressionAndIfComparisonExprChanger(val program: Program, val errors: IErrorReporter, val compTarget: ICompilationTarget) : AstWalker() {

    override fun before(expr: BinaryExpression, parent: Node): Iterable<IAstModification> {
        if(expr.operator=="==" || expr.operator=="!=") {
            val left = expr.left as? BinaryExpression
            if (left != null) {
                val rightValue = expr.right.constValue(program)
                if (rightValue?.number == 0.0 && rightValue.type.isIntegerOrBool) {
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

        // applying De Morgan's laws proved beneficial for the code generator,
        // when the code has one outer 'not' instead of two inner ones.
        if(expr.operator=="or" || expr.operator=="and") {

            // boolean case
            val newOper = if(expr.operator=="or") "and" else "or"
            val leftP = expr.left as? PrefixExpression
            val rightP = expr.right as? PrefixExpression
            if(leftP!=null && leftP.operator=="not" && rightP!=null && rightP.operator=="not") {
                // (not a) or (not b)  --> not(a and b)
                // (not a) and (not b) --> not(a or b)
                val inner = BinaryExpression(leftP.expression, newOper, rightP.expression, expr.position)
                val notExpr = PrefixExpression("not", inner, expr.position)
                return listOf(IAstModification.ReplaceNode(expr, notExpr, parent))
            }

            // integer case (only if both are the same type)
            val leftC = expr.left as? BinaryExpression
            val rightC = expr.right as? BinaryExpression
            if(expr.operator=="and" && leftC!=null && rightC!=null && leftC.operator=="==" && rightC.operator=="==") {
                if (leftC.right.constValue(program)?.number == 0.0 && rightC.right.constValue(program)?.number == 0.0) {
                    val leftDt = leftC.left.inferType(program).getOrUndef()
                    val rightDt = rightC.left.inferType(program).getOrUndef()
                    if(leftDt==rightDt && leftDt.isInteger) {
                        if (rightC.left.isSimple) {
                            // x==0 and y==0   ->  (x | y)==0
                            // the 'or' case cannot be easily optimized with a binary and like this!
                            val inner = BinaryExpression(leftC.left, "|", rightC.left, expr.position)
                            val compare = BinaryExpression(inner, "==", NumericLiteral(leftDt.base, 0.0, expr.position), expr.position)
                            return listOf(IAstModification.ReplaceNode(expr, compare, parent))
                        }
                    }
                }
            }

            // mixed cases
            if(leftC!=null && rightP!=null && leftC.operator=="==" && rightP.operator=="not") {
                // mixed case 1:   x==V or not y  ->  not(x!=V and y)
                val invertedLeftExpression = invertCondition(leftC, program)
                val inner = BinaryExpression(invertedLeftExpression, newOper, rightP.expression, expr.position)
                val notExpr = PrefixExpression("not", inner, expr.position)
                return listOf(IAstModification.ReplaceNode(expr, notExpr, parent))
            }
            else if(rightC!=null && leftP!=null && rightC.operator=="==" && leftP.operator=="not") {
                // mixed case 1:   not x or y==V  ->  not(x and y!=V)
                val invertedRightExpression = invertCondition(rightC, program)
                val inner = BinaryExpression(leftP.expression, newOper, invertedRightExpression, expr.position)
                val notExpr = PrefixExpression("not", inner, expr.position)
                return listOf(IAstModification.ReplaceNode(expr, notExpr, parent))
            }
        }

        return noModifications
    }

    override fun after(expr: PrefixExpression, parent: Node): Iterable<IAstModification> {
        if(expr.operator == "not") {
            // first check if we're already part of a "boolean" expression (i.e. comparing against 0 or 1)
            // if so, simplify THAT whole expression rather than making it more complicated
            if (parent is BinaryExpression) {
                if (parent.right.constValue(program)?.number == 0.0) {
                    if(parent.right.inferType(program).isBool) {
                        if(parent.operator=="==") {
                            // (NOT X)==false --> X==true --> X
                            return listOf(IAstModification.ReplaceNode(parent, expr.expression, parent.parent))
                        } else if(parent.operator=="!=") {
                            // (NOT X)!=false --> X!=true -> not X
                            return listOf(IAstModification.ReplaceNode(parent, expr, parent.parent))
                        }
                    } else {
                        if(parent.operator=="==") {
                            // (NOT X)==0 --> X!=0
                            val replacement = BinaryExpression(expr.expression, "!=", NumericLiteral.optimalInteger(0, expr.position), expr.position)
                            return listOf(IAstModification.ReplaceNode(parent, replacement, parent.parent))
                        } else if(parent.operator=="!=") {
                            // (NOT X)!=0 --> X==0
                            val replacement = BinaryExpression(expr.expression, "==", NumericLiteral.optimalInteger(0, expr.position), expr.position)
                            return listOf(IAstModification.ReplaceNode(parent, replacement, parent.parent))
                        }
                    }
                }
                else if (parent.right.constValue(program)?.number == 1.0) {
                    if(parent.right.inferType(program).isBool) {
                        if(parent.operator=="==") {
                            // (NOT X)==true --> X==false --> not X
                            return listOf(IAstModification.ReplaceNode(parent, expr, parent.parent))
                        } else if(parent.operator=="!=") {
                            // (NOT X)!=true --> X!=false -> X
                            return listOf(IAstModification.ReplaceNode(parent, expr.expression, parent.parent))
                        }
                    } else {
                        if(parent.operator=="==") {
                            // (NOT X)==1 --> X==0
                            val replacement = BinaryExpression(expr.expression, "==", NumericLiteral.optimalInteger(0, expr.position), expr.position)
                            return listOf(IAstModification.ReplaceNode(parent, replacement, parent.parent))
                        } else if(parent.operator=="!=") {
                            // (NOT X)!=1 --> X!=0
                            val replacement = BinaryExpression(expr.expression, "!=", NumericLiteral.optimalInteger(0, expr.position), expr.position)
                            return listOf(IAstModification.ReplaceNode(parent, replacement, parent.parent))
                        }
                    }
                }
            }

            // not(not(x)) -> x
            if((expr.expression as? PrefixExpression)?.operator=="not")
                return listOf(IAstModification.ReplaceNode(expr, (expr.expression as PrefixExpression).expression, parent))
            // not(~x) -> x!=0
            if((expr.expression as? PrefixExpression)?.operator=="~") {
                val x = (expr.expression as PrefixExpression).expression
                val dt = x.inferType(program).getOrUndef()
                if(!dt.isUndefined) {
                    require(dt.isNumeric)
                    val notZero = BinaryExpression(x, "!=", NumericLiteral(dt.base, 0.0, expr.position), expr.position)
                    return listOf(IAstModification.ReplaceNode(expr, notZero, parent))
                }
            }

            // not  X <compare> Y  ->   X <invertedcompare> Y
            val subBinExpr = expr.expression as? BinaryExpression
            if(subBinExpr!=null) {
                val invertedOperator = invertedComparisonOperator(subBinExpr.operator)
                if(invertedOperator!=null) {
                    val inverted = BinaryExpression(subBinExpr.left, invertedOperator, subBinExpr.right, subBinExpr.position)
                    return listOf(IAstModification.ReplaceNode(expr, inverted, parent))
                }
            }
        }

        return noModifications
    }

    override fun before(ifElse: IfElse, parent: Node): Iterable<IAstModification> {
        if(ifElse.elsepart.isNotEmpty()) {
            val prefix = ifElse.condition as? PrefixExpression
            if(prefix?.operator=="not") {
                // if not x  a else b ->  if x  b else a
                ifElse.condition = prefix.expression
                ifElse.condition.parent = ifElse
                val elsepart = ifElse.elsepart
                ifElse.elsepart = ifElse.truepart
                ifElse.truepart = elsepart
            }
        }
        return noModifications
    }

    override fun before(ifExpr: IfExpression, parent: Node): Iterable<IAstModification> {
        val prefix = ifExpr.condition as? PrefixExpression
        if(prefix?.operator=="not") {
            // if not x  a else b ->  if x  b else a
            ifExpr.condition = prefix.expression
            ifExpr.condition.parent = ifExpr
            val falsevalue = ifExpr.falsevalue
            ifExpr.falsevalue = ifExpr.truevalue
            ifExpr.truevalue = falsevalue
        }

        return noModifications
    }
}
