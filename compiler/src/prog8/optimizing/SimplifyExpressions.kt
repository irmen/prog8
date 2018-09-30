package prog8.optimizing

import prog8.ast.*
import prog8.compiler.HeapValues
import kotlin.math.abs

/*
    todo simplify expression terms:

        X*Y - X  ->  X*(Y-1)
        X*Y - Y  ->  Y*(X-1)
        -X + A ->  A - X
        X+ (-A) -> X - A
        X % 1 -> constant 0 (if X is byte/word)
        X % 2 -> X and 1 (if X is byte/word)

    todo expression optimization: remove redundant builtin function calls
    todo expression optimization: common (sub) expression elimination (turn common expressions into single subroutine call)

 */

class SimplifyExpressions(private val namespace: INameScope, private val heap: HeapValues) : IAstProcessor {
    var optimizationsDone: Int = 0

    override fun process(assignment: Assignment): IStatement {
        if(assignment.aug_op!=null) {
            throw AstException("augmented assignments should have been converted to normal assignments before this optimizer")
        }
        return super.process(assignment)
    }

    override fun process(expr: BinaryExpression): IExpression {
        super.process(expr)
        val leftVal = expr.left.constValue(namespace, heap)
        val rightVal = expr.right.constValue(namespace, heap)
        val constTrue = LiteralValue.fromBoolean(true, expr.position)
        val constFalse = LiteralValue.fromBoolean(false, expr.position)

        // simplify logical expressions when a term is constant and determines the outcome
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
            "/", "//" -> return optimizeDivision(expr, leftVal, rightVal)
            "+" -> return optimizeAdd(expr, leftVal, rightVal)
            "-" -> return optimizeSub(expr, leftVal, rightVal)
            "**" -> return optimizePower(expr, leftVal, rightVal)
        }
        return expr
    }

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
                    return FunctionCall(IdentifierReference(listOf("sqrt"), expr.position), listOf(expr.left), expr.position)
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

    private fun optimizeDivision(expr: BinaryExpression, leftVal: LiteralValue?, rightVal: LiteralValue?): IExpression {
        if(leftVal==null && rightVal==null)
            return expr

        if(rightVal!=null) {
            // right value is a constant, see if we can optimize
            val rightConst: LiteralValue = rightVal
            when(rightConst.asNumericValue?.toDouble()) {
                -1.0 -> {
                    //  '/' -> -left, '//' -> -ceil(left)
                    optimizationsDone++
                    when(expr.operator) {
                        "/" -> return PrefixExpression("-", expr.left, expr.position)
                        "//" -> return PrefixExpression("-",
                                FunctionCall(IdentifierReference(listOf("ceil"), expr.position), listOf(expr.left), expr.position),
                                expr.position)
                    }
                }
                1.0 -> {
                    //  '/' -> left, '//' -> floor(left)
                    optimizationsDone++
                    when(expr.operator) {
                        "/" -> return expr.left
                        "//" -> return FunctionCall(IdentifierReference(listOf("floor"), expr.position), listOf(expr.left), expr.position)
                    }
                }
            }

            if (expr.left.resultingDatatype(namespace, heap) == DataType.BYTE) {
                if(abs(rightConst.asNumericValue!!.toDouble()) >= 256.0) {
                    optimizationsDone++
                    return LiteralValue(DataType.BYTE, 0, position = expr.position)
                }
            }
            else if (expr.left.resultingDatatype(namespace, heap) == DataType.WORD) {
                if(abs(rightConst.asNumericValue!!.toDouble()) >= 65536.0) {
                    optimizationsDone++
                    return LiteralValue(DataType.BYTE, 0, position = expr.position)
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
            when(rightConst.asNumericValue?.toDouble()) {
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
            }
        }
        // no need to check for left val constant (because of associativity)

        return expr
    }
}