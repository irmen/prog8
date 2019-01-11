package prog8.optimizing

import prog8.ast.*
import prog8.compiler.HeapValues
import kotlin.math.abs
import kotlin.math.log2

/*
    todo simplify expression terms:

        X*Y - X  ->  X*(Y-1)   ???
        Y*X - X  ->  X*(Y-1)   ???

        value <associative_operator> X  --> X <associative_operator> value  <<<<< should be done already


    todo expression optimization: common (sub) expression elimination (turn common expressions into single subroutine call + introduce variable to hold it)

 */

class SimplifyExpressions(private val namespace: INameScope, private val heap: HeapValues) : IAstProcessor {
    var optimizationsDone: Int = 0

    override fun process(assignment: Assignment): IStatement {
        if(assignment.aug_op!=null)
            throw AstException("augmented assignments should have been converted to normal assignments before this optimizer")
        return super.process(assignment)
    }

    override fun process(expr: PrefixExpression): IExpression {
        if(expr.operator == "+") {
            // +X --> X
            optimizationsDone++
            return expr.expression.process(this)
        }
        return super.process(expr)
    }

    override fun process(expr: BinaryExpression): IExpression {
        super.process(expr)
        val leftVal = expr.left.constValue(namespace, heap)
        val rightVal = expr.right.constValue(namespace, heap)
        val constTrue = LiteralValue.fromBoolean(true, expr.position)
        val constFalse = LiteralValue.fromBoolean(false, expr.position)

        val leftDt = expr.left.resultingDatatype(namespace, heap)
        val rightDt = expr.right.resultingDatatype(namespace, heap)
        if(leftDt!=null && rightDt!=null && leftDt!=rightDt) {
            // try to convert a datatype into the other
            if(adjustDatatypes(expr, leftVal, leftDt, rightVal, rightDt)) {
                optimizationsDone++
                return expr
            }
        }

        // Value <associativeoperator> X -->  X <associativeoperator> Value
        if(leftVal!=null && expr.operator in associativeOperators && rightVal==null) {
            val tmp = expr.left
            expr.left = expr.right
            expr.right = tmp
            optimizationsDone++
            return expr
        }

        // X + (-A)  -->  X - A
        if(expr.operator=="+" && (expr.right as? PrefixExpression)?.operator=="-") {
            expr.operator = "-"
            expr.right = (expr.right as PrefixExpression).expression
            optimizationsDone++
            return expr
        }

        // (-A) + X  -->  X - A
        if(expr.operator=="+" && (expr.left as? PrefixExpression)?.operator=="-") {
            expr.operator = "-"
            val newRight = (expr.left as PrefixExpression).expression
            expr.left = expr.right
            expr.right = newRight
            optimizationsDone++
            return expr
        }

        // X + (-value)  -->  X - value
        if(expr.operator=="+" && rightVal!=null) {
            val rv = rightVal.asNumericValue?.toDouble()
            if(rv!=null && rv<0.0) {
                expr.operator = "-"
                expr.right = LiteralValue.fromNumber(-rv, rightVal.type, rightVal.position)
                optimizationsDone++
                return expr
            }
        }

        // (-value) + X  -->  X - value
        if(expr.operator=="+" && leftVal!=null) {
            val lv = leftVal.asNumericValue?.toDouble()
            if(lv!=null && lv<0.0) {
                expr.operator = "-"
                expr.right = LiteralValue.fromNumber(-lv, leftVal.type, leftVal.position)
                optimizationsDone++
                return expr
            }
        }

        // X - (-A)  -->  X + A
        if(expr.operator=="-" && (expr.right as? PrefixExpression)?.operator=="-") {
            expr.operator = "+"
            expr.right = (expr.right as PrefixExpression).expression
            optimizationsDone++
            return expr
        }

        // X - (-value)  -->  X + value
        if(expr.operator=="-" && rightVal!=null) {
            val rv = rightVal.asNumericValue?.toDouble()
            if(rv!=null && rv<0.0) {
                expr.operator = "+"
                expr.right = LiteralValue.fromNumber(-rv, rightVal.type, rightVal.position)
                optimizationsDone++
                return expr
            }
        }

        // simplify when a term is constant and determines the outcome
        when(expr.operator) {
            "or" -> {
                if((leftVal!=null && leftVal.asBooleanValue) || (rightVal!=null && rightVal.asBooleanValue)) {
                    optimizationsDone++
                    return constTrue
                }
                if(leftVal!=null && !leftVal.asBooleanValue) {
                    optimizationsDone++
                    return expr.right
                }
                if(rightVal!=null && !rightVal.asBooleanValue) {
                    optimizationsDone++
                    return expr.left
                }
            }
            "and" -> {
                if((leftVal!=null && !leftVal.asBooleanValue) || (rightVal!=null && !rightVal.asBooleanValue)) {
                    optimizationsDone++
                    return constFalse
                }
                if(leftVal!=null && leftVal.asBooleanValue) {
                    optimizationsDone++
                    return expr.right
                }
                if(rightVal!=null && rightVal.asBooleanValue) {
                    optimizationsDone++
                    return expr.left
                }
            }
            "xor" -> {
                if(leftVal!=null && !leftVal.asBooleanValue) {
                    optimizationsDone++
                    return expr.right
                }
                if(rightVal!=null && !rightVal.asBooleanValue) {
                    optimizationsDone++
                    return expr.left
                }
                if(leftVal!=null && leftVal.asBooleanValue) {
                    optimizationsDone++
                    return PrefixExpression("not", expr.right, expr.right.position)
                }
                if(rightVal!=null && rightVal.asBooleanValue) {
                    optimizationsDone++
                    return PrefixExpression("not", expr.left, expr.left.position)
                }
            }
            "|", "^" -> {
                if(leftVal!=null && !leftVal.asBooleanValue) {
                    optimizationsDone++
                    return expr.right
                }
                if(rightVal!=null && !rightVal.asBooleanValue) {
                    optimizationsDone++
                    return expr.left
                }
            }
            "&" -> {
                if(leftVal!=null && !leftVal.asBooleanValue) {
                    optimizationsDone++
                    return constFalse
                }
                if(rightVal!=null && !rightVal.asBooleanValue) {
                    optimizationsDone++
                    return constFalse
                }
            }
            "*" -> return optimizeMultiplication(expr, leftVal, rightVal)
            "/" -> return optimizeDivision(expr, leftVal, rightVal)
            "+" -> return optimizeAdd(expr, leftVal, rightVal)
            "-" -> return optimizeSub(expr, leftVal, rightVal)
            "**" -> return optimizePower(expr, leftVal, rightVal)
            "%" -> return optimizeRemainder(expr, leftVal, rightVal)
        }
        return expr
    }

    private fun adjustDatatypes(expr: BinaryExpression,
                                leftConstVal: LiteralValue?, leftDt: DataType,
                                rightConstVal: LiteralValue?, rightDt: DataType): Boolean {

        fun adjust(value: LiteralValue, targetDt: DataType): Pair<Boolean, LiteralValue>{
            if(value.type==targetDt)
                return Pair(false, value)
            when(value.type) {
                DataType.UBYTE -> {
                    if (targetDt == DataType.BYTE) {
                        if(value.bytevalue!! < 127)
                            return Pair(true, LiteralValue(targetDt, value.bytevalue, position=value.position))
                    }
                    else if (targetDt == DataType.UWORD || targetDt == DataType.WORD)
                        return Pair(true, LiteralValue(targetDt, wordvalue = value.bytevalue!!.toInt(), position=value.position))
                }
                DataType.BYTE -> {
                    if (targetDt == DataType.UBYTE) {
                        if(value.bytevalue!! >= 0)
                            return Pair(true, LiteralValue(targetDt, value.bytevalue, position=value.position))
                    }
                    else if (targetDt == DataType.UWORD) {
                        if(value.bytevalue!! >= 0)
                            return Pair(true, LiteralValue(targetDt, wordvalue=value.bytevalue.toInt(), position=value.position))
                    }
                    else if (targetDt == DataType.WORD) return Pair(true, LiteralValue(targetDt, wordvalue=value.bytevalue!!.toInt(), position=value.position))
                }
                DataType.UWORD -> {
                    if (targetDt == DataType.UBYTE) {
                        if(value.wordvalue!! <= 255)
                            return Pair(true, LiteralValue(targetDt, value.wordvalue.toShort(), position=value.position))
                    }
                    else if (targetDt == DataType.BYTE) {
                        if(value.wordvalue!! <= 127)
                            return Pair(true, LiteralValue(targetDt, value.wordvalue.toShort(), position=value.position))
                    }
                    else if (targetDt == DataType.WORD) {
                        if(value.wordvalue!! <= 32767)
                            return Pair(true, LiteralValue(targetDt, wordvalue=value.wordvalue, position=value.position))
                    }
                }
                DataType.WORD -> {
                    if (targetDt == DataType.UBYTE) {
                        if(value.wordvalue!! in 0..255)
                            return Pair(true, LiteralValue(targetDt, value.wordvalue.toShort(), position=value.position))
                    }
                    else if (targetDt == DataType.BYTE) {
                        if(value.wordvalue!! in -128..127)
                            return Pair(true, LiteralValue(targetDt, value.wordvalue.toShort(), position=value.position))
                    }
                    else if (targetDt == DataType.UWORD) {
                        if(value.wordvalue!! >= 0)
                            return Pair(true, LiteralValue(targetDt, value.wordvalue.toShort(), position=value.position))
                    }
                }
                else -> {}
            }
            return Pair(false, value)
        }

        if(leftConstVal==null && rightConstVal!=null) {
            val (adjusted, newValue) = adjust(rightConstVal, leftDt)
            if(adjusted) {
                expr.right = newValue
                optimizationsDone++
                return true
            }
            return false
        } else if(leftConstVal!=null && rightConstVal==null) {
            val (adjusted, newValue) = adjust(leftConstVal, rightDt)
            if(adjusted) {
                expr.left = newValue
                optimizationsDone++
                return true
            }
            return false
        } else {
            return false
        }
    }


    // todo: get rid of this?
    private data class ReorderedAssociativeBinaryExpr(val expr: BinaryExpression, val leftVal: LiteralValue?, val rightVal: LiteralValue?)

    private fun reorderAssociative(expr: BinaryExpression, leftVal: LiteralValue?): ReorderedAssociativeBinaryExpr {
        if(expr.operator in associativeOperators && leftVal!=null) {
            // swap left and right so that right is always the constant
            val tmp = expr.left
            expr.left = expr.right
            expr.right = tmp
            optimizationsDone++
            return ReorderedAssociativeBinaryExpr(expr, expr.right.constValue(namespace, heap), leftVal)
        }
        return ReorderedAssociativeBinaryExpr(expr, leftVal, expr.right.constValue(namespace, heap))
    }

    private fun optimizeAdd(pexpr: BinaryExpression, pleftVal: LiteralValue?, prightVal: LiteralValue?): IExpression {
        if(pleftVal==null && prightVal==null)
            return pexpr

        val (expr, _, rightVal) = reorderAssociative(pexpr, pleftVal)
        if(rightVal!=null) {
            // right value is a constant, see if we can optimize
            val rightConst: LiteralValue = rightVal
            when(rightConst.asNumericValue?.toDouble()) {
                0.0 -> {
                    // left
                    optimizationsDone++
                    return expr.left
                }
            }
        }
        // no need to check for left val constant (because of associativity)

        return expr
    }

    private fun optimizeSub(expr: BinaryExpression, leftVal: LiteralValue?, rightVal: LiteralValue?): IExpression {
        if(leftVal==null && rightVal==null)
            return expr

        if(rightVal!=null) {
            // right value is a constant, see if we can optimize
            val rightConst: LiteralValue = rightVal
            when(rightConst.asNumericValue?.toDouble()) {
                0.0 -> {
                    // left
                    optimizationsDone++
                    return expr.left
                }
            }
        }
        if(leftVal!=null) {
            // left value is a constant, see if we can optimize
            when(leftVal.asNumericValue?.toDouble()) {
                0.0 -> {
                    // -right
                    optimizationsDone++
                    return PrefixExpression("-", expr.right, expr.position)
                }
            }
        }

        return expr
    }

    private fun optimizePower(expr: BinaryExpression, leftVal: LiteralValue?, rightVal: LiteralValue?): IExpression {
        if(leftVal==null && rightVal==null)
            return expr

        if(rightVal!=null) {
            // right value is a constant, see if we can optimize
            val rightConst: LiteralValue = rightVal
            when(rightConst.asNumericValue?.toDouble()) {
                -3.0 -> {
                    // -1/(left*left*left)
                    optimizationsDone++
                    return BinaryExpression(LiteralValue(DataType.FLOAT, floatvalue = -1.0, position = expr.position), "/",
                            BinaryExpression(expr.left, "*", BinaryExpression(expr.left, "*", expr.left, expr.position), expr.position),
                            expr.position)
                }
                -2.0 -> {
                    // -1/(left*left)
                    optimizationsDone++
                    return BinaryExpression(LiteralValue(DataType.FLOAT, floatvalue = -1.0, position = expr.position), "/",
                            BinaryExpression(expr.left, "*", expr.left, expr.position),
                            expr.position)
                }
                -1.0 -> {
                    // -1/left
                    optimizationsDone++
                    return BinaryExpression(LiteralValue(DataType.FLOAT, floatvalue = -1.0, position = expr.position), "/",
                            expr.left, expr.position)
                }
                0.0 -> {
                    // 1
                    optimizationsDone++
                    return LiteralValue.fromNumber(1, rightConst.type, expr.position)
                }
                0.5 -> {
                    // sqrt(left)
                    optimizationsDone++
                    return FunctionCall(IdentifierReference(listOf("sqrt"), expr.position), mutableListOf(expr.left), expr.position)
                }
                1.0 -> {
                    // left
                    optimizationsDone++
                    return expr.left
                }
                2.0 -> {
                    // left*left
                    optimizationsDone++
                    return BinaryExpression(expr.left, "*", expr.left, expr.position)
                }
                3.0 -> {
                    // left*left*left
                    optimizationsDone++
                    return BinaryExpression(expr.left, "*", BinaryExpression(expr.left, "*", expr.left, expr.position), expr.position)
                }
            }
        }
        if(leftVal!=null) {
            // left value is a constant, see if we can optimize
            when(leftVal.asNumericValue?.toDouble()) {
                -1.0 -> {
                    // -1
                    optimizationsDone++
                    return LiteralValue(DataType.FLOAT, floatvalue = -1.0, position = expr.position)
                }
                0.0 -> {
                    // 0
                    optimizationsDone++
                    return LiteralValue.fromNumber(0, leftVal.type, expr.position)
                }
                1.0 -> {
                    //1
                    optimizationsDone++
                    return LiteralValue.fromNumber(1, leftVal.type, expr.position)
                }

            }
        }

        return expr
    }

    private fun optimizeRemainder(expr: BinaryExpression, leftVal: LiteralValue?, rightVal: LiteralValue?): IExpression {
        if(leftVal==null && rightVal==null)
            return expr

        // simplify assignments  A = B <operator> C

        val cv = rightVal?.asIntegerValue?.toDouble()
        when(expr.operator) {
            "%" -> {
                if (cv == 1.0) {
                    optimizationsDone++
                    return LiteralValue.fromNumber(0, expr.resultingDatatype(namespace, heap)!!, expr.position)
                } else if (cv == 2.0) {
                    optimizationsDone++
                    expr.operator = "&"
                    expr.right = LiteralValue.optimalInteger(1, expr.position)
                    return expr
                }
            }
        }
        return expr

    }

    private fun optimizeDivision(expr: BinaryExpression, leftVal: LiteralValue?, rightVal: LiteralValue?): IExpression {
        if(leftVal==null && rightVal==null)
            return expr

        // cannot shuffle assiciativity with division!

        if(rightVal!=null) {
            // right value is a constant, see if we can optimize
            val rightConst: LiteralValue = rightVal
            val cv = rightConst.asNumericValue?.toDouble()
            val leftDt = expr.left.resultingDatatype(namespace, heap)
            when(cv) {
                -1.0 -> {
                    //  '/' -> -left
                    if (expr.operator == "/") {
                        optimizationsDone++
                        return PrefixExpression("-", expr.left, expr.position)
                    }
                }
                1.0 -> {
                    //  '/' -> left
                    if (expr.operator == "/") {
                        optimizationsDone++
                        return expr.left
                    }
                }
                2.0, 4.0, 8.0, 16.0, 32.0, 64.0, 128.0, 256.0, 512.0, 1024.0, 2048.0, 4096.0, 8192.0, 16384.0, 32768.0, 65536.0 -> {
                    if(leftDt in IntegerDatatypes) {
                        // divided by a power of two => shift right
                        optimizationsDone++
                        val numshifts = log2(cv).toInt()
                        return BinaryExpression(expr.left, ">>", LiteralValue.optimalInteger(numshifts, expr.position), expr.position)
                    }
                }
                -2.0, -4.0, -8.0, -16.0, -32.0, -64.0, -128.0, -256.0, -512.0, -1024.0, -2048.0, -4096.0, -8192.0, -16384.0, -32768.0, -65536.0 -> {
                    if(leftDt in IntegerDatatypes) {
                        // divided by a negative power of two => negate, then shift right
                        optimizationsDone++
                        val numshifts = log2(-cv).toInt()
                        return BinaryExpression(PrefixExpression("-", expr.left, expr.position), ">>", LiteralValue.optimalInteger(numshifts, expr.position), expr.position)
                    }
                }
            }

            if (leftDt == DataType.UBYTE) {
                if(abs(rightConst.asNumericValue!!.toDouble()) >= 256.0) {
                    optimizationsDone++
                    return LiteralValue(DataType.UBYTE, 0, position = expr.position)
                }
            }
            else if (leftDt == DataType.UWORD) {
                if(abs(rightConst.asNumericValue!!.toDouble()) >= 65536.0) {
                    optimizationsDone++
                    return LiteralValue(DataType.UBYTE, 0, position = expr.position)
                }
            }
        }

        if(leftVal!=null) {
            // left value is a constant, see if we can optimize
            when(leftVal.asNumericValue?.toDouble()) {
                0.0 -> {
                    // 0
                    optimizationsDone++
                    return LiteralValue.fromNumber(0, leftVal.type, expr.position)
                }
            }
        }

        return expr
    }

    private fun optimizeMultiplication(pexpr: BinaryExpression, pleftVal: LiteralValue?, prightVal: LiteralValue?): IExpression {
        if(pleftVal==null && prightVal==null)
            return pexpr

        val (expr, _, rightVal) = reorderAssociative(pexpr, pleftVal)
        if(rightVal!=null) {
            // right value is a constant, see if we can optimize
            val leftValue: IExpression = expr.left
            val rightConst: LiteralValue = rightVal
            val cv = rightConst.asNumericValue?.toDouble()
            when(cv) {
                -1.0 -> {
                    // -left
                    optimizationsDone++
                    return PrefixExpression("-", leftValue, expr.position)
                }
                0.0 -> {
                    // 0
                    optimizationsDone++
                    return LiteralValue.fromNumber(0, rightConst.type, expr.position)
                }
                1.0 -> {
                    // left
                    optimizationsDone++
                    return expr.left
                }
                2.0, 4.0, 8.0, 16.0, 32.0, 64.0, 128.0, 256.0, 512.0, 1024.0, 2048.0, 4096.0, 8192.0, 16384.0, 32768.0, 65536.0 -> {
                    if(leftValue.resultingDatatype(namespace, heap) in IntegerDatatypes) {
                        // times a power of two => shift left
                        optimizationsDone++
                        val numshifts = log2(cv).toInt()
                        return BinaryExpression(expr.left, "<<", LiteralValue.optimalInteger(numshifts, expr.position), expr.position)
                    }
                }
                -2.0, -4.0, -8.0, -16.0, -32.0, -64.0, -128.0, -256.0, -512.0, -1024.0, -2048.0, -4096.0, -8192.0, -16384.0, -32768.0, -65536.0 -> {
                    if(leftValue.resultingDatatype(namespace, heap) in IntegerDatatypes) {
                        // times a negative power of two => negate, then shift left
                        optimizationsDone++
                        val numshifts = log2(-cv).toInt()
                        return BinaryExpression(PrefixExpression("-", expr.left, expr.position), "<<", LiteralValue.optimalInteger(numshifts, expr.position), expr.position)
                    }
                }
            }
        }
        // no need to check for left val constant (because of associativity)

        return expr
    }
}
