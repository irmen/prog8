package prog8.compiler.astprocessing

import prog8.ast.IStatementContainer
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.FatalAstException
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.compilerinterface.IErrorReporter


internal class VariousCleanups(val program: Program, val errors: IErrorReporter): AstWalker() {

    override fun before(nop: Nop, parent: Node): Iterable<IAstModification> {
        return listOf(IAstModification.Remove(nop, parent as IStatementContainer))
    }

    override fun after(scope: AnonymousScope, parent: Node): Iterable<IAstModification> {
        return if(parent is IStatementContainer)
            listOf(ScopeFlatten(scope, parent as IStatementContainer))
        else
            noModifications
    }

    private class ScopeFlatten(val scope: AnonymousScope, val into: IStatementContainer) : IAstModification {
        override fun perform() {
            val idx = into.statements.indexOf(scope)
            if(idx>=0) {
                into.statements.addAll(idx+1, scope.statements)
                scope.statements.forEach { it.parent = into as Node }
                into.statements.remove(scope)
            }
        }
    }

    override fun after(typecast: TypecastExpression, parent: Node): Iterable<IAstModification> {
        if(typecast.expression is NumericLiteralValue) {
            val value = (typecast.expression as NumericLiteralValue).cast(typecast.type)
            if(value.isValid)
                return listOf(IAstModification.ReplaceNode(typecast, value.valueOrZero(), parent))
        }

        val sourceDt = typecast.expression.inferType(program)
        if(sourceDt istype typecast.type)
            return listOf(IAstModification.ReplaceNode(typecast, typecast.expression, parent))

        if(parent is Assignment) {
            val targetDt = (parent).target.inferType(program).getOrElse { throw FatalAstException("invalid dt") }
            if(sourceDt istype targetDt) {
                // we can get rid of this typecast because the type is already
                return listOf(IAstModification.ReplaceNode(typecast, typecast.expression, parent))
            }
        }

        return noModifications
    }

    override fun after(assignment: Assignment, parent: Node): Iterable<IAstModification> {
        val nextAssign = assignment.nextSibling() as? Assignment
        if(nextAssign!=null && nextAssign.target.isSameAs(assignment.target, program)) {
            if(nextAssign.value isSameAs assignment.value)
                return listOf(IAstModification.Remove(assignment, parent as IStatementContainer))
        }

        return noModifications
    }

    override fun after(expr: PrefixExpression, parent: Node): Iterable<IAstModification> {
        if(expr.operator=="+") {
            // +X --> X
            return listOf(IAstModification.ReplaceNode(expr, expr.expression, parent))
        }
        if(expr.operator=="not") {
            val nestedPrefix = expr.expression as? PrefixExpression
            if(nestedPrefix!=null && nestedPrefix.operator=="not") {
                // NOT NOT X  -->  X
                return listOf(IAstModification.ReplaceNode(expr, nestedPrefix.expression, parent))
            }
            val comparison = expr.expression as? BinaryExpression
            if (comparison != null) {
                // NOT COMPARISON ==> inverted COMPARISON
                val invertedOperator = invertedComparisonOperator(comparison.operator)
                if (invertedOperator != null) {
                    comparison.operator = invertedOperator
                    return listOf(IAstModification.ReplaceNode(expr, comparison, parent))
                }
            }
        }
        return noModifications
    }

    override fun before(expr: BinaryExpression, parent: Node): Iterable<IAstModification> {
        if(expr.operator == "or") {
            val leftBinExpr = expr.left as? BinaryExpression
            val rightBinExpr = expr.right as? BinaryExpression
            if(leftBinExpr!=null && leftBinExpr.operator=="==" && rightBinExpr!=null && rightBinExpr.operator=="==") {
                if(leftBinExpr.right is NumericLiteralValue && rightBinExpr.right is NumericLiteralValue) {
                    errors.warn("consider using when statement to test for multiple values", expr.position)
                }
            }
        }
        return noModifications
    }

    override fun after(expr: BinaryExpression, parent: Node): Iterable<IAstModification> {
        if(expr.operator in ComparisonOperators) {
            val leftConstVal = expr.left.constValue(program)
            val rightConstVal = expr.right.constValue(program)
            // make sure the constant value is on the right of the comparison expression
            if(rightConstVal==null && leftConstVal!=null) {
                val newOperator =
                    when(expr.operator) {
                        "<" -> ">"
                        "<=" -> ">="
                        ">" -> "<"
                        ">=" -> "<="
                        else -> expr.operator
                    }
                val replacement = BinaryExpression(expr.right, newOperator, expr.left, expr.position)
                return listOf(IAstModification.ReplaceNode(expr, replacement, parent))
            }
        }
        return noModifications
    }
}
