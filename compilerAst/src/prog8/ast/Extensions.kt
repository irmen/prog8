package prog8.ast

import prog8.ast.expressions.BinaryExpression
import prog8.ast.expressions.Expression


fun maySwapOperandOrder(binexpr: BinaryExpression): Boolean {
    fun ok(expr: Expression): Boolean {
        return when(expr) {
            is BinaryExpression -> expr.left.isSimple
            is IFunctionCall -> false
            else -> expr.isSimple
        }
    }
    return ok(binexpr.left) || ok(binexpr.right)
}
