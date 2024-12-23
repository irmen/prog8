package prog8.optimizer

import prog8.ast.ExpressionError
import prog8.ast.FatalAstException
import prog8.ast.Program
import prog8.ast.expressions.FunctionCallExpression
import prog8.ast.expressions.NumericLiteral
import prog8.code.core.BaseDataType
import prog8.code.core.Position
import prog8.code.core.isInteger
import prog8.code.core.isIntegerOrBool
import kotlin.math.*


class ConstExprEvaluator {

    fun evaluate(left: NumericLiteral, operator: String, right: NumericLiteral): NumericLiteral {
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

    private fun shiftedright(left: NumericLiteral, amount: NumericLiteral): NumericLiteral {
        if(!left.type.isInteger || !amount.type.isInteger)
            throw ExpressionError("cannot compute $left >> $amount", left.position)
        val result =
                if(left.type == BaseDataType.UBYTE || left.type == BaseDataType.UWORD)
                    left.number.toInt().ushr(amount.number.toInt())
                else
                    left.number.toInt().shr(amount.number.toInt())
        return NumericLiteral(left.type, result.toDouble(), left.position)
    }

    private fun shiftedleft(left: NumericLiteral, amount: NumericLiteral): NumericLiteral {
        if(!left.type.isInteger || !amount.type.isInteger)
            throw ExpressionError("cannot compute $left << $amount", left.position)
        val result = left.number.toInt().shl(amount.number.toInt())
//        when(left.type) {
//            DataType.BOOL -> result = result and 1
//            DataType.UBYTE -> result = result and 255
//            DataType.BYTE -> result = result.toByte().toInt()
//            DataType.UWORD -> result = result and 65535
//            DataType.WORD -> result = result.toShort().toInt()
//            else -> { /* keep as it is */ }
//        }
        return NumericLiteral.optimalNumeric(left.type, null, result.toDouble(), left.position)
    }

    private fun bitwiseXor(left: NumericLiteral, right: NumericLiteral): NumericLiteral {
        if(left.type==BaseDataType.UBYTE || left.type==BaseDataType.BOOL) {
            if(right.type.isIntegerOrBool) {
                return NumericLiteral(BaseDataType.UBYTE, (left.number.toInt() xor (right.number.toInt() and 255)).toDouble(), left.position)
            }
        } else if(left.type==BaseDataType.UWORD) {
            if(right.type.isInteger) {
                return NumericLiteral(BaseDataType.UWORD, (left.number.toInt() xor right.number.toInt() and 65535).toDouble(), left.position)
            }
        } else if(left.type==BaseDataType.LONG) {
            if(right.type.isInteger) {
                return NumericLiteral.optimalNumeric((left.number.toInt() xor right.number.toInt()).toDouble(), left.position)
            }
        }

        throw ExpressionError("cannot calculate $left ^ $right", left.position)
    }

    private fun bitwiseOr(left: NumericLiteral, right: NumericLiteral): NumericLiteral {
        if(left.type==BaseDataType.UBYTE || left.type==BaseDataType.BOOL) {
            if(right.type.isIntegerOrBool) {
                return NumericLiteral(BaseDataType.UBYTE, (left.number.toInt() or (right.number.toInt() and 255)).toDouble(), left.position)
            }
        } else if(left.type==BaseDataType.UWORD) {
            if(right.type.isInteger) {
                return NumericLiteral(BaseDataType.UWORD, (left.number.toInt() or right.number.toInt() and 65535).toDouble(), left.position)
            }
        } else if(left.type==BaseDataType.LONG) {
            if(right.type.isInteger) {
                return NumericLiteral.optimalNumeric((left.number.toInt() or right.number.toInt()).toDouble(), left.position)
            }
        }
        throw ExpressionError("cannot calculate $left | $right", left.position)
    }

    private fun bitwiseAnd(left: NumericLiteral, right: NumericLiteral): NumericLiteral {
        if(left.type==BaseDataType.UBYTE || left.type==BaseDataType.BOOL) {
            if(right.type.isIntegerOrBool) {
                return NumericLiteral(BaseDataType.UBYTE, (left.number.toInt() and (right.number.toInt() and 255)).toDouble(), left.position)
            }
        } else if(left.type==BaseDataType.UWORD) {
            if(right.type.isInteger) {
                return NumericLiteral(BaseDataType.UWORD, (left.number.toInt() and right.number.toInt() and 65535).toDouble(), left.position)
            }
        } else if(left.type==BaseDataType.LONG) {
            if(right.type.isInteger) {
                return NumericLiteral.optimalNumeric((left.number.toInt() and right.number.toInt()).toDouble(), left.position)
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
        return when {
            left.type.isInteger -> when {
                right.type.isInteger -> NumericLiteral.optimalInteger(left.type, right.type, left.number.toInt() + right.number.toInt(), left.position)
                right.type == BaseDataType.FLOAT -> NumericLiteral(BaseDataType.FLOAT, left.number.toInt() + right.number, left.position)
                else -> throw ExpressionError(error, left.position)
            }
            left.type == BaseDataType.FLOAT -> when {
                right.type.isInteger -> NumericLiteral(BaseDataType.FLOAT, left.number + right.number.toInt(), left.position)
                right.type == BaseDataType.FLOAT -> NumericLiteral(BaseDataType.FLOAT, left.number + right.number, left.position)
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
    }

    private fun minus(left: NumericLiteral, right: NumericLiteral): NumericLiteral {
        val error = "cannot subtract $left and $right"
        return when {
            left.type.isInteger -> when {
                right.type.isInteger -> NumericLiteral.optimalInteger(left.type, right.type, left.number.toInt() - right.number.toInt(), left.position)
                right.type == BaseDataType.FLOAT -> NumericLiteral(BaseDataType.FLOAT, left.number.toInt() - right.number, left.position)
                else -> throw ExpressionError(error, left.position)
            }
            left.type == BaseDataType.FLOAT -> when {
                right.type.isInteger -> NumericLiteral(BaseDataType.FLOAT, left.number - right.number.toInt(), left.position)
                right.type == BaseDataType.FLOAT -> NumericLiteral(BaseDataType.FLOAT, left.number - right.number, left.position)
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
    }

    private fun multiply(left: NumericLiteral, right: NumericLiteral): NumericLiteral {
        val error = "cannot multiply ${left.type} and ${right.type}"
        return when {
            left.type.isInteger -> when {
                right.type.isInteger -> NumericLiteral.optimalInteger(left.type, right.type, left.number.toInt() * right.number.toInt(), left.position)
                right.type == BaseDataType.FLOAT -> NumericLiteral(BaseDataType.FLOAT, left.number.toInt() * right.number, left.position)
                else -> throw ExpressionError(error, left.position)
            }
            left.type == BaseDataType.FLOAT -> when {
                right.type.isInteger -> NumericLiteral(BaseDataType.FLOAT, left.number * right.number.toInt(), left.position)
                right.type == BaseDataType.FLOAT -> NumericLiteral(BaseDataType.FLOAT, left.number * right.number, left.position)
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
    }

    private fun divideByZeroError(pos: Position): Unit =
            throw ExpressionError("division by zero", pos)

    private fun divide(left: NumericLiteral, right: NumericLiteral): NumericLiteral {
        val error = "cannot divide $left by $right"
        return when {
            left.type.isInteger -> when {
                right.type.isInteger -> {
                    if(right.number.toInt()==0) divideByZeroError(right.position)
                    val result: Int = left.number.toInt() / right.number.toInt()
                    NumericLiteral.optimalInteger(left.type, right.type, result, left.position)
                }
                right.type == BaseDataType.FLOAT -> {
                    if(right.number==0.0) divideByZeroError(right.position)
                    NumericLiteral(BaseDataType.FLOAT, left.number.toInt() / right.number, left.position)
                }
                else -> throw ExpressionError(error, left.position)
            }
            left.type == BaseDataType.FLOAT -> when {
                right.type.isInteger -> {
                    if(right.number.toInt()==0) divideByZeroError(right.position)
                    NumericLiteral(BaseDataType.FLOAT, left.number / right.number.toInt(), left.position)
                }
                right.type == BaseDataType.FLOAT -> {
                    if(right.number ==0.0) divideByZeroError(right.position)
                    NumericLiteral(BaseDataType.FLOAT, left.number / right.number, left.position)
                }
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
    }

    private fun remainder(left: NumericLiteral, right: NumericLiteral): NumericLiteral {
        val error = "cannot compute remainder of $left by $right"
        return when {
            left.type.isInteger -> when {
                right.type.isInteger -> {
                    if(right.number.toInt()==0) divideByZeroError(right.position)
                    NumericLiteral.optimalNumeric(left.type, right.type, left.number.toInt().toDouble() % right.number.toInt().toDouble(), left.position)
                }
                right.type == BaseDataType.FLOAT -> {
                    if(right.number ==0.0) divideByZeroError(right.position)
                    NumericLiteral(BaseDataType.FLOAT, left.number.toInt() % right.number, left.position)
                }
                else -> throw ExpressionError(error, left.position)
            }
            left.type == BaseDataType.FLOAT -> when {
                right.type.isInteger -> {
                    if(right.number.toInt()==0) divideByZeroError(right.position)
                    NumericLiteral(BaseDataType.FLOAT, left.number % right.number.toInt(), left.position)
                }
                right.type == BaseDataType.FLOAT -> {
                    if(right.number ==0.0) divideByZeroError(right.position)
                    NumericLiteral(BaseDataType.FLOAT, left.number % right.number, left.position)
                }
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
    }

    fun evaluate(call: FunctionCallExpression, program: Program): NumericLiteral? {
        if(call.target.nameInSource.size!=2)
            return null  // likely a builtin function, or user function, these get evaluated elsewhere
        val constArgs = call.args.mapNotNull { it.constValue(program) }
        if(constArgs.size!=call.args.size)
            return null

        return when(call.target.nameInSource[0]) {
            "math" -> evalMath(call, constArgs)
            "floats" -> evalFloats(call, constArgs)
            "strings" -> evalString(call, constArgs)
            else -> null
        }
    }

    private fun evalFloats(func: FunctionCallExpression, args: List<NumericLiteral>): NumericLiteral? {
        val result= when(func.target.nameInSource[1]) {
            "pow" -> args[0].number.pow(args[1].number)
            "sin" -> sin(args[0].number)
            "cos" -> cos(args[0].number)
            "tan" -> tan(args[0].number)
            "atan" -> atan(args[0].number)
            "ln" -> ln(args[0].number)
            "log2" -> log2(args[0].number)
            "rad" -> args[0].number/360.0 * 2 * PI
            "deg" -> args[0].number/ 2 / PI * 360.0
            "round" -> round(args[0].number)
            "floor" -> floor(args[0].number)
            "ceil" -> ceil(args[0].number)
            "minf", "min" -> min(args[0].number, args[1].number)
            "maxf", "max" -> max(args[0].number, args[1].number)
            "clampf", "clamp" -> {
                var value = args[0].number
                val minimum = args[1].number
                val maximum = args[2].number
                if(value<minimum)
                    value=minimum
                if(value<maximum)
                    value
                else
                    maximum
            }
            else -> null
        }

        return if(result==null)
            null
        else
            NumericLiteral(BaseDataType.FLOAT, result, func.position)
    }

    private fun evalMath(func: FunctionCallExpression, args: List<NumericLiteral>): NumericLiteral? {
        return when(func.target.nameInSource[1]) {
            "sin8u" -> {
                val value = truncate(128.0 + 127.5 * sin(args.single().number / 256.0 * 2 * PI))
                NumericLiteral(BaseDataType.UBYTE, value, func.position)
            }
            "cos8u" -> {
                val value = truncate(128.0 + 127.5 * cos(args.single().number / 256.0 * 2 * PI))
                NumericLiteral(BaseDataType.UBYTE, value, func.position)
            }
            "sin8" -> {
                val value = truncate(127.0  * sin(args.single().number / 256.0 * 2 * PI))
                NumericLiteral(BaseDataType.BYTE, value, func.position)
            }
            "cos8" -> {
                val value = truncate(127.0  * cos(args.single().number / 256.0 * 2 * PI))
                NumericLiteral(BaseDataType.BYTE, value, func.position)
            }
            "sinr8u" -> {
                val value = truncate(128.0 + 127.5 * sin(args.single().number / 180.0 * 2 * PI))
                NumericLiteral(BaseDataType.UBYTE, value, func.position)
            }
            "cosr8u" -> {
                val value = truncate(128.0 + 127.5 * cos(args.single().number / 180.0 * 2 * PI))
                NumericLiteral(BaseDataType.UBYTE, value, func.position)
            }
            "sinr8" -> {
                val value = truncate(127.0  * sin(args.single().number / 180.0 * 2 * PI))
                NumericLiteral(BaseDataType.BYTE, value, func.position)
            }
            "cosr8" -> {
                val value = truncate(127.0  * cos(args.single().number / 180.0 * 2 * PI))
                NumericLiteral(BaseDataType.BYTE, value, func.position)
            }
            "log2" -> {
                val value = truncate(log2(args.single().number))
                NumericLiteral(BaseDataType.UBYTE, value, func.position)
            }
            "log2w" -> {
                val value = truncate(log2(args.single().number))
                NumericLiteral(BaseDataType.UWORD, value, func.position)
            }
            "atan2" -> {
                val x1f = args[0].number
                val y1f = args[1].number
                val x2f = args[2].number
                val y2f = args[3].number
                var radians = atan2(y2f-y1f, x2f-x1f)
                if(radians<0)
                    radians+=2*PI
                NumericLiteral(BaseDataType.UWORD, floor(radians/2.0/PI*256.0), func.position)
            }
            "diff" -> {
                val n1 = args[0].number
                val n2 = args[1].number
                val value = if(n1>n2) n1-n2 else n2-n1
                NumericLiteral(BaseDataType.UBYTE, value, func.position)
            }
            "diffw" -> {
                val n1 = args[0].number
                val n2 = args[1].number
                val value = if(n1>n2) n1-n2 else n2-n1
                NumericLiteral(BaseDataType.UWORD, value, func.position)
            }
            else -> null
        }
    }

    private fun evalString(func: FunctionCallExpression, args: List<NumericLiteral>): NumericLiteral? {
        return when(func.target.nameInSource[1]) {
            "isdigit" -> {
                val char = args[0].number.toInt()
                NumericLiteral.fromBoolean(char in 48..57, func.position)
            }
            "isupper" -> {
                // shifted petscii has 2 ranges that contain the upper case letters... 97-122 and 193-218
                val char = args[0].number.toInt()
                NumericLiteral.fromBoolean(char in 97..122 || char in 193..218, func.position)
            }
            "islower" -> {
                val char = args[0].number.toInt()
                NumericLiteral.fromBoolean(char in 65..90, func.position)
            }
            "isletter" -> {
                val char = args[0].number.toInt()
                NumericLiteral.fromBoolean(char in 65..90 || char in 97..122 || char in 193..218, func.position)
            }
            "isspace" -> {
                val char = args[0].number.toInt()
                NumericLiteral.fromBoolean(char in arrayOf(32, 13, 9, 10, 141, 160), func.position)
            }
            "isprint" -> {
                val char = args[0].number.toInt()
                NumericLiteral.fromBoolean(char in 32..127 || char>=160, func.position)
            }
            else -> null
        }
    }
}
