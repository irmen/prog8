package prog8.optimizer

import prog8.ast.*
import prog8.ast.base.*
import prog8.ast.expressions.NumericLiteralValue
import kotlin.math.pow


class ConstExprEvaluator {

    fun evaluate(left: NumericLiteralValue, operator: String, right: NumericLiteralValue): IExpression {
        return when(operator) {
            "+" -> plus(left, right)
            "-" -> minus(left, right)
            "*" -> multiply(left, right)
            "/" -> divide(left, right)
            "%" -> remainder(left, right)
            "**" -> power(left, right)
            "&" -> bitwiseand(left, right)
            "|" -> bitwiseor(left, right)
            "^" -> bitwisexor(left, right)
            "and" -> logicaland(left, right)
            "or" -> logicalor(left, right)
            "xor" -> logicalxor(left, right)
            "<" -> NumericLiteralValue.fromBoolean(left < right, left.position)
            ">" -> NumericLiteralValue.fromBoolean(left > right, left.position)
            "<=" -> NumericLiteralValue.fromBoolean(left <= right, left.position)
            ">=" -> NumericLiteralValue.fromBoolean(left >= right, left.position)
            "==" -> NumericLiteralValue.fromBoolean(left == right, left.position)
            "!=" -> NumericLiteralValue.fromBoolean(left != right, left.position)
            "<<" -> shiftedleft(left, right)
            ">>" -> shiftedright(left, right)
            else -> throw FatalAstException("const evaluation for invalid operator $operator")
        }
    }

    private fun shiftedright(left: NumericLiteralValue, amount: NumericLiteralValue): IExpression {
        if(left.type !in IntegerDatatypes || amount.type !in IntegerDatatypes)
            throw ExpressionError("cannot compute $left >> $amount", left.position)
        val result =
                if(left.type== DataType.UBYTE || left.type== DataType.UWORD)
                    left.number.toInt().ushr(amount.number.toInt())
                else
                    left.number.toInt().shr(amount.number.toInt())
        return NumericLiteralValue(left.type, result, left.position)
    }

    private fun shiftedleft(left: NumericLiteralValue, amount: NumericLiteralValue): IExpression {
        if(left.type !in IntegerDatatypes || amount.type !in IntegerDatatypes)
            throw ExpressionError("cannot compute $left << $amount", left.position)
        val result = left.number.toInt().shl(amount.number.toInt())
        return NumericLiteralValue(left.type, result, left.position)
    }

    private fun logicalxor(left: NumericLiteralValue, right: NumericLiteralValue): NumericLiteralValue {
        val error = "cannot compute $left locical-bitxor $right"
        return when {
            left.type in IntegerDatatypes -> when {
                right.type in IntegerDatatypes -> NumericLiteralValue.fromBoolean((left.number.toInt() != 0) xor (right.number.toInt() != 0), left.position)
                right.type == DataType.FLOAT -> NumericLiteralValue.fromBoolean((left.number.toInt() != 0) xor (right.number.toDouble() != 0.0), left.position)
                else -> throw ExpressionError(error, left.position)
            }
            left.type == DataType.FLOAT -> when {
                right.type in IntegerDatatypes -> NumericLiteralValue.fromBoolean((left.number.toDouble() != 0.0) xor (right.number.toInt() != 0), left.position)
                right.type == DataType.FLOAT -> NumericLiteralValue.fromBoolean((left.number.toDouble() != 0.0) xor (right.number.toDouble() != 0.0), left.position)
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
    }

    private fun logicalor(left: NumericLiteralValue, right: NumericLiteralValue): NumericLiteralValue {
        val error = "cannot compute $left locical-or $right"
        return when {
            left.type in IntegerDatatypes -> when {
                right.type in IntegerDatatypes -> NumericLiteralValue.fromBoolean(left.number.toInt() != 0 || right.number.toInt() != 0, left.position)
                right.type == DataType.FLOAT -> NumericLiteralValue.fromBoolean(left.number.toInt() != 0 || right.number.toDouble() != 0.0, left.position)
                else -> throw ExpressionError(error, left.position)
            }
            left.type == DataType.FLOAT -> when {
                right.type in IntegerDatatypes -> NumericLiteralValue.fromBoolean(left.number.toDouble() != 0.0 || right.number.toInt() != 0, left.position)
                right.type == DataType.FLOAT -> NumericLiteralValue.fromBoolean(left.number.toDouble() != 0.0 || right.number.toDouble() != 0.0, left.position)
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
    }

    private fun logicaland(left: NumericLiteralValue, right: NumericLiteralValue): NumericLiteralValue {
        val error = "cannot compute $left locical-and $right"
        return when {
            left.type in IntegerDatatypes -> when {
                right.type in IntegerDatatypes -> NumericLiteralValue.fromBoolean(left.number.toInt() != 0 && right.number.toInt() != 0, left.position)
                right.type == DataType.FLOAT -> NumericLiteralValue.fromBoolean(left.number.toInt() != 0 && right.number.toDouble() != 0.0, left.position)
                else -> throw ExpressionError(error, left.position)
            }
            left.type == DataType.FLOAT -> when {
                right.type in IntegerDatatypes -> NumericLiteralValue.fromBoolean(left.number.toDouble() != 0.0 && right.number.toInt() != 0, left.position)
                right.type == DataType.FLOAT -> NumericLiteralValue.fromBoolean(left.number.toDouble() != 0.0 && right.number.toDouble() != 0.0, left.position)
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
    }

    private fun bitwisexor(left: NumericLiteralValue, right: NumericLiteralValue): NumericLiteralValue {
        if(left.type== DataType.UBYTE) {
            if(right.type in IntegerDatatypes) {
                return NumericLiteralValue(DataType.UBYTE, (left.number.toInt() xor (right.number.toInt() and 255)).toShort(), left.position)
            }
        } else if(left.type== DataType.UWORD) {
            if(right.type in IntegerDatatypes) {
                return NumericLiteralValue(DataType.UWORD, left.number.toInt() xor right.number.toInt(), left.position)
            }
        }
        throw ExpressionError("cannot calculate $left ^ $right", left.position)
    }

    private fun bitwiseor(left: NumericLiteralValue, right: NumericLiteralValue): NumericLiteralValue {
        if(left.type== DataType.UBYTE) {
            if(right.type in IntegerDatatypes) {
                return NumericLiteralValue(DataType.UBYTE, (left.number.toInt() or (right.number.toInt() and 255)).toShort(), left.position)
            }
        } else if(left.type== DataType.UWORD) {
            if(right.type in IntegerDatatypes) {
                return NumericLiteralValue(DataType.UWORD, left.number.toInt() or right.number.toInt(), left.position)
            }
        }
        throw ExpressionError("cannot calculate $left | $right", left.position)
    }

    private fun bitwiseand(left: NumericLiteralValue, right: NumericLiteralValue): NumericLiteralValue {
        if(left.type== DataType.UBYTE) {
            if(right.type in IntegerDatatypes) {
                return NumericLiteralValue(DataType.UBYTE, (left.number.toInt() or (right.number.toInt() and 255)).toShort(), left.position)
            }
        } else if(left.type== DataType.UWORD) {
            if(right.type in IntegerDatatypes) {
                return NumericLiteralValue(DataType.UWORD, left.number.toInt() or right.number.toInt(), left.position)
            }
        }
        throw ExpressionError("cannot calculate $left & $right", left.position)
    }

    private fun power(left: NumericLiteralValue, right: NumericLiteralValue): NumericLiteralValue {
        val error = "cannot calculate $left ** $right"
        return when {
            left.type in IntegerDatatypes -> when {
                right.type in IntegerDatatypes -> NumericLiteralValue.optimalNumeric(left.number.toInt().toDouble().pow(right.number.toInt()), left.position)
                right.type == DataType.FLOAT -> NumericLiteralValue(DataType.FLOAT, left.number.toInt().toDouble().pow(right.number.toDouble()), left.position)
                else -> throw ExpressionError(error, left.position)
            }
            left.type == DataType.FLOAT -> when {
                right.type in IntegerDatatypes -> NumericLiteralValue(DataType.FLOAT, left.number.toDouble().pow(right.number.toInt()), left.position)
                right.type == DataType.FLOAT -> NumericLiteralValue(DataType.FLOAT, left.number.toDouble().pow(right.number.toDouble()), left.position)
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
    }

    private fun plus(left: NumericLiteralValue, right: NumericLiteralValue): NumericLiteralValue {
        val error = "cannot add $left and $right"
        return when {
            left.type in IntegerDatatypes -> when {
                right.type in IntegerDatatypes -> NumericLiteralValue.optimalNumeric(left.number.toInt() + right.number.toInt(), left.position)
                right.type == DataType.FLOAT -> NumericLiteralValue(DataType.FLOAT, left.number.toInt() + right.number.toDouble(), left.position)
                else -> throw ExpressionError(error, left.position)
            }
            left.type == DataType.FLOAT -> when {
                right.type in IntegerDatatypes -> NumericLiteralValue(DataType.FLOAT, left.number.toDouble() + right.number.toInt(), left.position)
                right.type == DataType.FLOAT -> NumericLiteralValue(DataType.FLOAT, left.number.toDouble() + right.number.toDouble(), left.position)
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
    }

    private fun minus(left: NumericLiteralValue, right: NumericLiteralValue): NumericLiteralValue {
        val error = "cannot subtract $left and $right"
        return when {
            left.type in IntegerDatatypes -> when {
                right.type in IntegerDatatypes -> NumericLiteralValue.optimalNumeric(left.number.toInt() - right.number.toInt(), left.position)
                right.type == DataType.FLOAT -> NumericLiteralValue(DataType.FLOAT, left.number.toInt() - right.number.toDouble(), left.position)
                else -> throw ExpressionError(error, left.position)
            }
            left.type == DataType.FLOAT -> when {
                right.type in IntegerDatatypes -> NumericLiteralValue(DataType.FLOAT, left.number.toDouble() - right.number.toInt(), left.position)
                right.type == DataType.FLOAT -> NumericLiteralValue(DataType.FLOAT, left.number.toDouble() - right.number.toDouble(), left.position)
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
    }

    private fun multiply(left: NumericLiteralValue, right: NumericLiteralValue): NumericLiteralValue {
        val error = "cannot multiply ${left.type} and ${right.type}"
        return when {
            left.type in IntegerDatatypes -> when {
                right.type in IntegerDatatypes -> NumericLiteralValue.optimalNumeric(left.number.toInt() * right.number.toInt(), left.position)
                right.type == DataType.FLOAT -> NumericLiteralValue(DataType.FLOAT, left.number.toInt() * right.number.toDouble(), left.position)
                else -> throw ExpressionError(error, left.position)
            }
            left.type == DataType.FLOAT -> when {
                right.type in IntegerDatatypes -> NumericLiteralValue(DataType.FLOAT, left.number.toDouble() * right.number.toInt(), left.position)
                right.type == DataType.FLOAT -> NumericLiteralValue(DataType.FLOAT, left.number.toDouble() * right.number.toDouble(), left.position)
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
    }

    private fun divideByZeroError(pos: Position): Unit =
            throw ExpressionError("division by zero", pos)

    private fun divide(left: NumericLiteralValue, right: NumericLiteralValue): NumericLiteralValue {
        val error = "cannot divide $left by $right"
        return when {
            left.type in IntegerDatatypes -> when {
                right.type in IntegerDatatypes -> {
                    if(right.number.toInt()==0) divideByZeroError(right.position)
                    val result: Int = left.number.toInt() / right.number.toInt()
                    NumericLiteralValue.optimalNumeric(result, left.position)
                }
                right.type == DataType.FLOAT -> {
                    if(right.number.toDouble()==0.0) divideByZeroError(right.position)
                    NumericLiteralValue(DataType.FLOAT, left.number.toInt() / right.number.toDouble(), left.position)
                }
                else -> throw ExpressionError(error, left.position)
            }
            left.type == DataType.FLOAT -> when {
                right.type in IntegerDatatypes -> {
                    if(right.number.toInt()==0) divideByZeroError(right.position)
                    NumericLiteralValue(DataType.FLOAT, left.number.toDouble() / right.number.toInt(), left.position)
                }
                right.type == DataType.FLOAT -> {
                    if(right.number.toDouble()==0.0) divideByZeroError(right.position)
                    NumericLiteralValue(DataType.FLOAT, left.number.toDouble() / right.number.toDouble(), left.position)
                }
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
    }

    private fun remainder(left: NumericLiteralValue, right: NumericLiteralValue): NumericLiteralValue {
        val error = "cannot compute remainder of $left by $right"
        return when {
            left.type in IntegerDatatypes -> when {
                right.type in IntegerDatatypes -> {
                    if(right.number.toInt()==0) divideByZeroError(right.position)
                    NumericLiteralValue.optimalNumeric(left.number.toInt().toDouble() % right.number.toInt().toDouble(), left.position)
                }
                right.type == DataType.FLOAT -> {
                    if(right.number.toDouble()==0.0) divideByZeroError(right.position)
                    NumericLiteralValue(DataType.FLOAT, left.number.toInt() % right.number.toDouble(), left.position)
                }
                else -> throw ExpressionError(error, left.position)
            }
            left.type == DataType.FLOAT -> when {
                right.type in IntegerDatatypes -> {
                    if(right.number.toInt()==0) divideByZeroError(right.position)
                    NumericLiteralValue(DataType.FLOAT, left.number.toDouble() % right.number.toInt(), left.position)
                }
                right.type == DataType.FLOAT -> {
                    if(right.number.toDouble()==0.0) divideByZeroError(right.position)
                    NumericLiteralValue(DataType.FLOAT, left.number.toDouble() % right.number.toDouble(), left.position)
                }
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
    }
}
