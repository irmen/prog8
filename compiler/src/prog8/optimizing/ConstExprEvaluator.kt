package prog8.optimizing

import prog8.ast.*
import kotlin.math.pow


val associativeOperators = setOf("+", "*", "&", "|", "^", "or", "and", "xor", "==", "!=")


class ConstExprEvaluator {

    fun evaluate(left: LiteralValue, operator: String, right: LiteralValue): IExpression {
        return when(operator) {
            "+" -> plus(left, right)
            "-" -> minus(left, right)
            "*" -> multiply(left, right)
            "/" -> divide(left, right)
            "//" -> floordivide(left, right)
            "%" -> remainder(left, right)
            "**" -> power(left, right)
            "&" -> bitwiseand(left, right)
            "|" -> bitwiseor(left, right)
            "^" -> bitwisexor(left, right)
            "and" -> logicaland(left, right)
            "or" -> logicalor(left, right)
            "xor" -> logicalxor(left, right)
            "<" -> LiteralValue.fromBoolean(left < right, left.position)
            ">" -> LiteralValue.fromBoolean(left > right, left.position)
            "<=" -> LiteralValue.fromBoolean(left <= right, left.position)
            ">=" -> LiteralValue.fromBoolean(left >= right, left.position)
            "==" -> LiteralValue.fromBoolean(left == right, left.position)
            "!=" -> LiteralValue.fromBoolean(left != right, left.position)
            else -> throw FatalAstException("const evaluation for invalid operator $operator")
        }
    }

    private fun logicalxor(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot compute $left locical-bitxor $right"
        return when {
            left.asIntegerValue!=null -> when {
                right.asIntegerValue!=null -> LiteralValue.fromBoolean((left.asIntegerValue != 0) xor (right.asIntegerValue != 0), left.position)
                right.floatvalue!=null -> LiteralValue.fromBoolean((left.asIntegerValue != 0) xor (right.floatvalue != 0.0), left.position)
                else -> throw ExpressionError(error, left.position)
            }
            left.floatvalue!=null -> when {
                right.asIntegerValue!=null -> LiteralValue.fromBoolean((left.floatvalue != 0.0) xor (right.asIntegerValue != 0), left.position)
                right.floatvalue!=null -> LiteralValue.fromBoolean((left.floatvalue != 0.0) xor (right.floatvalue != 0.0), left.position)
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
    }

    private fun logicalor(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot compute $left locical-or $right"
        return when {
            left.asIntegerValue!=null -> when {
                right.asIntegerValue!=null -> LiteralValue.fromBoolean(left.asIntegerValue != 0 || right.asIntegerValue != 0, left.position)
                right.floatvalue!=null -> LiteralValue.fromBoolean(left.asIntegerValue != 0 || right.floatvalue != 0.0, left.position)
                else -> throw ExpressionError(error, left.position)
            }
            left.floatvalue!=null -> when {
                right.asIntegerValue!=null -> LiteralValue.fromBoolean(left.floatvalue != 0.0 || right.asIntegerValue != 0, left.position)
                right.floatvalue!=null -> LiteralValue.fromBoolean(left.floatvalue != 0.0 || right.floatvalue != 0.0, left.position)
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
    }

    private fun logicaland(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot compute $left locical-and $right"
        return when {
            left.asIntegerValue!=null -> when {
                right.asIntegerValue!=null -> LiteralValue.fromBoolean(left.asIntegerValue != 0 && right.asIntegerValue != 0, left.position)
                right.floatvalue!=null -> LiteralValue.fromBoolean(left.asIntegerValue != 0 && right.floatvalue != 0.0, left.position)
                else -> throw ExpressionError(error, left.position)
            }
            left.floatvalue!=null -> when {
                right.asIntegerValue!=null -> LiteralValue.fromBoolean(left.floatvalue != 0.0 && right.asIntegerValue != 0, left.position)
                right.floatvalue!=null -> LiteralValue.fromBoolean(left.floatvalue != 0.0 && right.floatvalue != 0.0, left.position)
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
    }

    private fun bitwisexor(left: LiteralValue, right: LiteralValue): LiteralValue {
        if(left.type== DataType.UBYTE) {
            if(right.asIntegerValue!=null) {
                return LiteralValue(DataType.UBYTE, bytevalue = (left.bytevalue!!.toInt() xor (right.asIntegerValue and 255)).toShort(), position = left.position)
            }
        } else if(left.type== DataType.UWORD) {
            if(right.asIntegerValue!=null) {
                return LiteralValue(DataType.UWORD, wordvalue = left.wordvalue!! xor right.asIntegerValue, position = left.position)
            }
        }
        throw ExpressionError("cannot calculate $left ^ $right", left.position)
    }

    private fun bitwiseor(left: LiteralValue, right: LiteralValue): LiteralValue {
        if(left.type== DataType.UBYTE) {
            if(right.asIntegerValue!=null) {
                return LiteralValue(DataType.UBYTE, bytevalue = (left.bytevalue!!.toInt() or (right.asIntegerValue and 255)).toShort(), position = left.position)
            }
        } else if(left.type== DataType.UWORD) {
            if(right.asIntegerValue!=null) {
                return LiteralValue(DataType.UWORD, wordvalue = left.wordvalue!! or right.asIntegerValue, position = left.position)
            }
        }
        throw ExpressionError("cannot calculate $left | $right", left.position)
    }

    private fun bitwiseand(left: LiteralValue, right: LiteralValue): LiteralValue {
        if(left.type== DataType.UBYTE) {
            if(right.asIntegerValue!=null) {
                return LiteralValue(DataType.UBYTE, bytevalue = (left.bytevalue!!.toInt() or (right.asIntegerValue and 255)).toShort(), position = left.position)
            }
        } else if(left.type== DataType.UWORD) {
            if(right.asIntegerValue!=null) {
                return LiteralValue(DataType.UWORD, wordvalue = left.wordvalue!! or right.asIntegerValue, position = left.position)
            }
        }
        throw ExpressionError("cannot calculate $left & $right", left.position)
    }

    private fun power(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot calculate $left ** $right"
        return when {
            left.asIntegerValue!=null -> when {
                right.asIntegerValue!=null -> LiteralValue.optimalNumeric(left.asIntegerValue.toDouble().pow(right.asIntegerValue), left.position)
                right.floatvalue!=null -> LiteralValue(DataType.FLOAT, floatvalue = left.asIntegerValue.toDouble().pow(right.floatvalue), position = left.position)
                else -> throw ExpressionError(error, left.position)
            }
            left.floatvalue!=null -> when {
                right.asIntegerValue!=null -> LiteralValue(DataType.FLOAT, floatvalue = left.floatvalue.pow(right.asIntegerValue), position = left.position)
                right.floatvalue!=null -> LiteralValue(DataType.FLOAT, floatvalue = left.floatvalue.pow(right.floatvalue), position = left.position)
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
    }

    private fun plus(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot add $left and $right"
        return when {
            left.asIntegerValue!=null -> when {
                right.asIntegerValue!=null -> LiteralValue.optimalNumeric(left.asIntegerValue + right.asIntegerValue, left.position)
                right.floatvalue!=null -> LiteralValue(DataType.FLOAT, floatvalue = left.asIntegerValue + right.floatvalue, position = left.position)
                else -> throw ExpressionError(error, left.position)
            }
            left.floatvalue!=null -> when {
                right.asIntegerValue!=null -> LiteralValue(DataType.FLOAT, floatvalue = left.floatvalue + right.asIntegerValue, position = left.position)
                right.floatvalue!=null -> LiteralValue(DataType.FLOAT, floatvalue = left.floatvalue + right.floatvalue, position = left.position)
                else -> throw ExpressionError(error, left.position)
            }
            left.strvalue!=null -> when {
                right.strvalue!=null -> {
                    val newStr = left.strvalue + right.strvalue
                    if(newStr.length > 255) throw ExpressionError("string too long", left.position)
                    LiteralValue(DataType.STR, strvalue = newStr, position = left.position)
                }
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
    }

    private fun minus(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot subtract $left and $right"
        return when {
            left.asIntegerValue!=null -> when {
                right.asIntegerValue!=null -> LiteralValue.optimalNumeric(left.asIntegerValue - right.asIntegerValue, left.position)
                right.floatvalue!=null -> LiteralValue(DataType.FLOAT, floatvalue = left.asIntegerValue - right.floatvalue, position = left.position)
                else -> throw ExpressionError(error, left.position)
            }
            left.floatvalue!=null -> when {
                right.asIntegerValue!=null -> LiteralValue(DataType.FLOAT, floatvalue = left.floatvalue - right.asIntegerValue, position = left.position)
                right.floatvalue!=null -> LiteralValue(DataType.FLOAT, floatvalue = left.floatvalue - right.floatvalue, position = left.position)
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
    }

    private fun multiply(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot multiply ${left.type} and ${right.type}"
        return when {
            left.asIntegerValue!=null -> when {
                right.asIntegerValue!=null -> LiteralValue.optimalNumeric(left.asIntegerValue * right.asIntegerValue, left.position)
                right.floatvalue!=null -> LiteralValue(DataType.FLOAT, floatvalue = left.asIntegerValue * right.floatvalue, position = left.position)
                right.strvalue!=null -> {
                    if(right.strvalue.length * left.asIntegerValue > 255) throw ExpressionError("string too long", left.position)
                    LiteralValue(DataType.STR, strvalue = right.strvalue.repeat(left.asIntegerValue), position = left.position)
                }
                else -> throw ExpressionError(error, left.position)
            }
            left.floatvalue!=null -> when {
                right.asIntegerValue!=null -> LiteralValue(DataType.FLOAT, floatvalue = left.floatvalue * right.asIntegerValue, position = left.position)
                right.floatvalue!=null -> LiteralValue(DataType.FLOAT, floatvalue = left.floatvalue * right.floatvalue, position = left.position)
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
    }

    private fun divideByZeroError(pos: Position): Unit =
            throw ExpressionError("division by zero", pos)

    private fun divide(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot divide $left by $right"
        return when {
            left.asIntegerValue!=null -> when {
                right.asIntegerValue!=null -> {
                    if(right.asIntegerValue==0) divideByZeroError(right.position)
                    val result = left.asIntegerValue.toDouble() / right.asIntegerValue.toDouble()
                    LiteralValue.optimalNumeric(result, left.position)
                }
                right.floatvalue!=null -> {
                    if(right.floatvalue==0.0) divideByZeroError(right.position)
                    LiteralValue(DataType.FLOAT, floatvalue = left.asIntegerValue / right.floatvalue, position = left.position)
                }
                else -> throw ExpressionError(error, left.position)
            }
            left.floatvalue!=null -> when {
                right.asIntegerValue!=null -> {
                    if(right.asIntegerValue==0) divideByZeroError(right.position)
                    LiteralValue(DataType.FLOAT, floatvalue = left.floatvalue / right.asIntegerValue, position = left.position)
                }
                right.floatvalue!=null -> {
                    if(right.floatvalue==0.0) divideByZeroError(right.position)
                    LiteralValue(DataType.FLOAT, floatvalue = left.floatvalue / right.floatvalue, position = left.position)
                }
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
    }

    private fun floordivide(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot floordivide $left by $right"
        return when {
            left.asIntegerValue!=null -> when {
                right.asIntegerValue!=null -> {
                    if(right.asIntegerValue==0) divideByZeroError(right.position)
                    LiteralValue.optimalInteger(left.asIntegerValue / right.asIntegerValue, left.position)
                }
                right.floatvalue!=null -> {
                    if(right.floatvalue==0.0) divideByZeroError(right.position)
                    LiteralValue.optimalInteger(left.asIntegerValue / right.floatvalue, left.position)
                }
                else -> throw ExpressionError(error, left.position)
            }
            left.floatvalue!=null -> when {
                right.asIntegerValue!=null -> {
                    if(right.asIntegerValue==0) divideByZeroError(right.position)
                    LiteralValue.optimalInteger(left.floatvalue / right.asIntegerValue, left.position)
                }
                right.floatvalue!=null -> {
                    if(right.floatvalue==0.0) divideByZeroError(right.position)
                    LiteralValue.optimalInteger(left.floatvalue / right.floatvalue, left.position)
                }
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
    }

    private fun remainder(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot compute remainder of $left by $right"
        return when {
            left.asIntegerValue!=null -> when {
                right.asIntegerValue!=null -> {
                    if(right.asIntegerValue==0) divideByZeroError(right.position)
                    LiteralValue.optimalNumeric(left.asIntegerValue.toDouble() % right.asIntegerValue.toDouble(), left.position)
                }
                right.floatvalue!=null -> {
                    if(right.floatvalue==0.0) divideByZeroError(right.position)
                    LiteralValue(DataType.FLOAT, floatvalue = left.asIntegerValue % right.floatvalue, position = left.position)
                }
                else -> throw ExpressionError(error, left.position)
            }
            left.floatvalue!=null -> when {
                right.asIntegerValue!=null -> {
                    if(right.asIntegerValue==0) divideByZeroError(right.position)
                    LiteralValue(DataType.FLOAT, floatvalue = left.floatvalue % right.asIntegerValue, position = left.position)
                }
                right.floatvalue!=null -> {
                    if(right.floatvalue==0.0) divideByZeroError(right.position)
                    LiteralValue(DataType.FLOAT, floatvalue = left.floatvalue % right.floatvalue, position = left.position)
                }
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
    }
}