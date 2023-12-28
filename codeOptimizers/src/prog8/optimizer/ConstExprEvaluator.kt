package prog8.optimizer

import prog8.ast.base.ExpressionError
import prog8.ast.base.FatalAstException
import prog8.ast.expressions.Expression
import prog8.ast.expressions.NumericLiteral
import prog8.code.core.DataType
import prog8.code.core.IntegerDatatypes
import prog8.code.core.Position


class ConstExprEvaluator {

    fun evaluate(left: NumericLiteral, operator: String, right: NumericLiteral): Expression {
        try {
            return when(operator) {
                "+" -> plus(left, right)
                "-" -> minus(left, right)
                "*" -> multiply(left, right)
                "/" -> divide(left, right)
                "%" -> remainder(left, right)
                "&" -> bitwiseAnd(left, right)
                "|" -> bitwiseOr(left, right)
                "^" -> bitwiseXor(left, right)
                "<" -> NumericLiteral.fromBoolean(left < right, left.position)
                ">" -> NumericLiteral.fromBoolean(left > right, left.position)
                "<=" -> NumericLiteral.fromBoolean(left <= right, left.position)
                ">=" -> NumericLiteral.fromBoolean(left >= right, left.position)
                "==" -> NumericLiteral.fromBoolean(left == right, left.position)
                "!=" -> NumericLiteral.fromBoolean(left != right, left.position)
                "<<" -> shiftedleft(left, right)
                ">>" -> shiftedright(left, right)
                "and" -> logicalAnd(left, right)
                "or" -> logicalOr(left, right)
                "xor" -> logicalXor(left, right)
                else -> throw FatalAstException("const evaluation for invalid operator $operator")
            }
        } catch (ax: FatalAstException) {
            throw ExpressionError(ax.message, left.position)
        }
    }

    private fun shiftedright(left: NumericLiteral, amount: NumericLiteral): Expression {
        if(left.type !in IntegerDatatypes || amount.type !in IntegerDatatypes)
            throw ExpressionError("cannot compute $left >> $amount", left.position)
        val result =
                if(left.type== DataType.UBYTE || left.type== DataType.UWORD)
                    left.number.toInt().ushr(amount.number.toInt())
                else
                    left.number.toInt().shr(amount.number.toInt())
        return NumericLiteral(left.type, result.toDouble(), left.position)
    }

    private fun shiftedleft(left: NumericLiteral, amount: NumericLiteral): Expression {
        if(left.type !in IntegerDatatypes || amount.type !in IntegerDatatypes)
            throw ExpressionError("cannot compute $left << $amount", left.position)
        val result = left.number.toInt().shl(amount.number.toInt())
        return NumericLiteral(left.type, result.toDouble(), left.position)
    }

    private fun bitwiseXor(left: NumericLiteral, right: NumericLiteral): NumericLiteral {
        if(left.type== DataType.UBYTE) {
            if(right.type in IntegerDatatypes) {
                return NumericLiteral(DataType.UBYTE, (left.number.toInt() xor (right.number.toInt() and 255)).toDouble(), left.position)
            }
        } else if(left.type== DataType.UWORD) {
            if(right.type in IntegerDatatypes) {
                return NumericLiteral(DataType.UWORD, (left.number.toInt() xor right.number.toInt()).toDouble(), left.position)
            }
        }
        throw ExpressionError("cannot calculate $left ^ $right", left.position)
    }

    private fun bitwiseOr(left: NumericLiteral, right: NumericLiteral): NumericLiteral {
        if(left.type== DataType.UBYTE) {
            if(right.type in IntegerDatatypes) {
                return NumericLiteral(DataType.UBYTE, (left.number.toInt() or (right.number.toInt() and 255)).toDouble(), left.position)
            }
        } else if(left.type== DataType.UWORD) {
            if(right.type in IntegerDatatypes) {
                return NumericLiteral(DataType.UWORD, (left.number.toInt() or right.number.toInt()).toDouble(), left.position)
            }
        }
        throw ExpressionError("cannot calculate $left | $right", left.position)
    }

    private fun bitwiseAnd(left: NumericLiteral, right: NumericLiteral): NumericLiteral {
        if(left.type== DataType.UBYTE) {
            if(right.type in IntegerDatatypes) {
                return NumericLiteral(DataType.UBYTE, (left.number.toInt() and (right.number.toInt() and 255)).toDouble(), left.position)
            }
        } else if(left.type== DataType.UWORD) {
            if(right.type in IntegerDatatypes) {
                return NumericLiteral(DataType.UWORD, (left.number.toInt() and right.number.toInt()).toDouble(), left.position)
            }
        }
        throw ExpressionError("cannot calculate $left & $right", left.position)
    }

    private fun logicalAnd(left: NumericLiteral, right: NumericLiteral): NumericLiteral =
        NumericLiteral.fromBoolean(left.asBooleanValue and right.asBooleanValue, left.position)

    private fun logicalOr(left: NumericLiteral, right: NumericLiteral): NumericLiteral =
        NumericLiteral.fromBoolean(left.asBooleanValue or right.asBooleanValue, left.position)

    private fun logicalXor(left: NumericLiteral, right: NumericLiteral): NumericLiteral =
        NumericLiteral.fromBoolean(left.asBooleanValue xor right.asBooleanValue, left.position)

    private fun plus(left: NumericLiteral, right: NumericLiteral): NumericLiteral {
        val error = "cannot add $left and $right"
        return when (left.type) {
            in IntegerDatatypes -> when (right.type) {
                in IntegerDatatypes -> NumericLiteral.optimalInteger(left.number.toInt() + right.number.toInt(), left.position)
                DataType.FLOAT -> NumericLiteral(DataType.FLOAT, left.number.toInt() + right.number, left.position)
                else -> throw ExpressionError(error, left.position)
            }
            DataType.FLOAT -> when (right.type) {
                in IntegerDatatypes -> NumericLiteral(DataType.FLOAT, left.number + right.number.toInt(), left.position)
                DataType.FLOAT -> NumericLiteral(DataType.FLOAT, left.number + right.number, left.position)
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
    }

    private fun minus(left: NumericLiteral, right: NumericLiteral): NumericLiteral {
        val error = "cannot subtract $left and $right"
        return when (left.type) {
            in IntegerDatatypes -> when (right.type) {
                in IntegerDatatypes -> NumericLiteral.optimalInteger(left.number.toInt() - right.number.toInt(), left.position)
                DataType.FLOAT -> NumericLiteral(DataType.FLOAT, left.number.toInt() - right.number, left.position)
                else -> throw ExpressionError(error, left.position)
            }
            DataType.FLOAT -> when (right.type) {
                in IntegerDatatypes -> NumericLiteral(DataType.FLOAT, left.number - right.number.toInt(), left.position)
                DataType.FLOAT -> NumericLiteral(DataType.FLOAT, left.number - right.number, left.position)
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
    }

    private fun multiply(left: NumericLiteral, right: NumericLiteral): NumericLiteral {
        val error = "cannot multiply ${left.type} and ${right.type}"
        return when (left.type) {
            in IntegerDatatypes -> when (right.type) {
                in IntegerDatatypes -> NumericLiteral.optimalInteger(left.number.toInt() * right.number.toInt(), left.position)
                DataType.FLOAT -> NumericLiteral(DataType.FLOAT, left.number.toInt() * right.number, left.position)
                else -> throw ExpressionError(error, left.position)
            }
            DataType.FLOAT -> when (right.type) {
                in IntegerDatatypes -> NumericLiteral(DataType.FLOAT, left.number * right.number.toInt(), left.position)
                DataType.FLOAT -> NumericLiteral(DataType.FLOAT, left.number * right.number, left.position)
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
    }

    private fun divideByZeroError(pos: Position): Unit =
            throw ExpressionError("division by zero", pos)

    private fun divide(left: NumericLiteral, right: NumericLiteral): NumericLiteral {
        val error = "cannot divide $left by $right"
        return when (left.type) {
            in IntegerDatatypes -> when (right.type) {
                in IntegerDatatypes -> {
                    if(right.number.toInt()==0) divideByZeroError(right.position)
                    val result: Int = left.number.toInt() / right.number.toInt()
                    NumericLiteral.optimalInteger(result, left.position)
                }
                DataType.FLOAT -> {
                    if(right.number==0.0) divideByZeroError(right.position)
                    NumericLiteral(DataType.FLOAT, left.number.toInt() / right.number, left.position)
                }
                else -> throw ExpressionError(error, left.position)
            }
            DataType.FLOAT -> when (right.type) {
                in IntegerDatatypes -> {
                    if(right.number.toInt()==0) divideByZeroError(right.position)
                    NumericLiteral(DataType.FLOAT, left.number / right.number.toInt(), left.position)
                }
                DataType.FLOAT -> {
                    if(right.number ==0.0) divideByZeroError(right.position)
                    NumericLiteral(DataType.FLOAT, left.number / right.number, left.position)
                }
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
    }

    private fun remainder(left: NumericLiteral, right: NumericLiteral): NumericLiteral {
        val error = "cannot compute remainder of $left by $right"
        return when (left.type) {
            in IntegerDatatypes -> when (right.type) {
                in IntegerDatatypes -> {
                    if(right.number.toInt()==0) divideByZeroError(right.position)
                    NumericLiteral.optimalNumeric(left.number.toInt().toDouble() % right.number.toInt().toDouble(), left.position)
                }
                DataType.FLOAT -> {
                    if(right.number ==0.0) divideByZeroError(right.position)
                    NumericLiteral(DataType.FLOAT, left.number.toInt() % right.number, left.position)
                }
                else -> throw ExpressionError(error, left.position)
            }
            DataType.FLOAT -> when (right.type) {
                in IntegerDatatypes -> {
                    if(right.number.toInt()==0) divideByZeroError(right.position)
                    NumericLiteral(DataType.FLOAT, left.number % right.number.toInt(), left.position)
                }
                DataType.FLOAT -> {
                    if(right.number ==0.0) divideByZeroError(right.position)
                    NumericLiteral(DataType.FLOAT, left.number % right.number, left.position)
                }
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
    }
}
