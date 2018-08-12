package il65

import il65.ast.*
import kotlin.math.pow


fun Module.optimized() : Module {
    val optimizer = AstOptimizer()
    var result = this.process(optimizer)
    while(optimizer.optimizationsDone>0) {
        println("Optimizations done: ${optimizer.optimizationsDone}")
        optimizer.reset()
        result = result.process(optimizer)
    }
    println("nothing left to process!")
    return result
}


class AstOptimizer : IAstProcessor {

    var optimizationsDone: Int = 0
        private set

    fun reset() {
        optimizationsDone = 0
    }

    /**
     * Try to process a unary prefix expression.
     * Compile-time constant sub expressions will be evaluated on the spot.
     * For instance, the expression for "- 4.5" will be optimized into the float literal -4.5
     */
    override fun process(expr: PrefixExpression): IExpression {
        expr.expression = expr.expression.process(this)    // process sub expression first

        val subexpr = expr.expression
        if (subexpr is LiteralValue) {
            // process prefixed literal values (such as -3, not true)
            return when {
                expr.operator == "+" -> subexpr
                expr.operator == "-" -> return when {
                    subexpr.intvalue != null -> {
                        optimizationsDone++
                        LiteralValue(intvalue = subexpr.intvalue, position = subexpr.position)
                    }
                    subexpr.floatvalue != null -> {
                        optimizationsDone++
                        LiteralValue(floatvalue = -subexpr.floatvalue, position = subexpr.position)
                    }
                    else -> throw UnsupportedOperationException("can only take negative of int or float")
                }
                expr.operator == "~" -> return when {
                    subexpr.intvalue != null -> {
                        optimizationsDone++
                        LiteralValue(intvalue = subexpr.intvalue.inv(), position = subexpr.position)
                    }
                    else -> throw UnsupportedOperationException("can only take bitwise inversion of int")
                }
                expr.operator == "not" -> return when {
                    subexpr.intvalue != null -> {
                        optimizationsDone++
                        LiteralValue(intvalue = if (subexpr.intvalue == 0) 1 else 0, position = subexpr.position)
                    }
                    subexpr.floatvalue != null -> {
                        optimizationsDone++
                        LiteralValue(intvalue = if (subexpr.floatvalue == 0.0) 1 else 0, position = subexpr.position)
                    }
                    else -> throw UnsupportedOperationException("can not take logical not of $subexpr")
                }
                else -> throw UnsupportedOperationException(expr.operator)
            }
        }
        return expr
    }

    /**
     * Try to process a binary expression.
     * Compile-time constant sub expressions will be evaluated on the spot.
     * For instance, "9 * (4 + 2)" will be optimized into the integer literal 54.
     */
    override fun process(expr: BinaryExpression): IExpression {
        val evaluator = ConstExprEvaluator()
        // process sub expressions first
        expr.left = expr.left.process(this)
        expr.right = expr.right.process(this)

        val leftconst = expr.left.constValue()
        val rightconst = expr.right.constValue()
        return when {
            leftconst != null && rightconst != null -> {
                println("optimizing $expr")
                optimizationsDone++
                evaluator.evaluate(leftconst, expr.operator, rightconst)
            }
            else -> expr
        }
    }

    override fun process(directive: Directive): IStatement {
        println("directove OPT $directive")
        return directive
    }
}


class ConstExprEvaluator {

    fun evaluate(left: LiteralValue, operator: String, right: LiteralValue): IExpression {
        return when(operator) {
            "+" -> plus(left, right)
            "-" -> minus(left, right)
            "*" -> multiply(left, right)
            "/" -> divide(left, right)
            "**" -> power(left, right)
            "<<" -> shiftleft(left, right)
            ">>" -> shiftright(left, right)
            "<<@" -> rotateleft(left, right)
            ">>@" -> rotateright(left, right)
            "&" -> bitwiseand(left, right)
            "|" -> bitwiseor(left, right)
            "^" -> bitwisexor(left, right)
            "and" -> logicaland(left, right)
            "or" -> logicalor(left, right)
            "xor" -> logicalxor(left, right)
            "<" -> compareless(left, right)
            ">" -> comparegreater(left, right)
            "<=" -> comparelessequal(left, right)
            ">=" -> comparegreaterequal(left, right)
            "==" -> compareequal(left, right)
            "!=" -> comparenotequal(left, right)
            else -> throw AstException("const evaluation for invalid operator $operator")
        }
    }

    private fun comparenotequal(left: LiteralValue, right: LiteralValue): LiteralValue {
        val leq = compareequal(left, right)
        return LiteralValue(intvalue = if(leq.intvalue==1) 0 else 1, position = left.position)
    }

    private fun compareequal(left: LiteralValue, right: LiteralValue): LiteralValue {
        val leftvalue: Any = when {
            left.intvalue!=null -> left.intvalue
            left.floatvalue!=null -> left.floatvalue
            left.strvalue!=null -> left.strvalue
            left.arrayvalue!=null -> left.arrayvalue
            else -> throw AstException("missing literal value")
        }
        val rightvalue: Any = when {
            right.intvalue!=null -> right.intvalue
            right.floatvalue!=null -> right.floatvalue
            right.strvalue!=null -> right.strvalue
            right.arrayvalue!=null -> right.arrayvalue
            else -> throw AstException("missing literal value")
        }
        return LiteralValue(intvalue = if(leftvalue==rightvalue) 1 else 0, position = left.position)
    }

    private fun comparegreaterequal(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot compute $left >= $right"
        return when {
            left.intvalue!=null -> when {
                right.intvalue!=null -> LiteralValue(
                        intvalue = if (left.intvalue >= right.intvalue) 1 else 0,
                        position = left.position)
                right.floatvalue!=null -> LiteralValue(
                        intvalue = if (left.intvalue >= right.floatvalue) 1 else 0,
                        position = left.position)
                else -> throw ExpressionException(error)
            }
            left.floatvalue!=null -> when {
                right.intvalue!=null -> LiteralValue(
                        intvalue = if (left.floatvalue >= right.intvalue) 1 else 0,
                        position = left.position)
                right.floatvalue!=null -> LiteralValue(
                        intvalue = if (left.floatvalue >= right.floatvalue) 1 else 0,
                        position = left.position)
                else -> throw ExpressionException(error)
            }
            else -> throw ExpressionException(error)
        }
    }

    private fun comparelessequal(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot compute $left >= $right"
        return when {
            left.intvalue!=null -> when {
                right.intvalue!=null -> LiteralValue(
                        intvalue = if (left.intvalue <= right.intvalue) 1 else 0,
                        position = left.position)
                right.floatvalue!=null -> LiteralValue(
                        intvalue = if (left.intvalue <= right.floatvalue) 1 else 0,
                        position = left.position)
                else -> throw ExpressionException(error)
            }
            left.floatvalue!=null -> when {
                right.intvalue!=null -> LiteralValue(
                        intvalue = if (left.floatvalue <= right.intvalue) 1 else 0,
                        position = left.position)
                right.floatvalue!=null -> LiteralValue(
                        intvalue = if (left.floatvalue <= right.floatvalue) 1 else 0,
                        position = left.position)
                else -> throw ExpressionException(error)
            }
            else -> throw ExpressionException(error)
        }
    }

    private fun comparegreater(left: LiteralValue, right: LiteralValue): LiteralValue {
        val leq = comparelessequal(left, right)
        return LiteralValue(intvalue = if(leq.intvalue==1) 0 else 1, position = left.position)
    }

    private fun compareless(left: LiteralValue, right: LiteralValue): LiteralValue {
        val leq = comparegreaterequal(left, right)
        return LiteralValue(intvalue = if(leq.intvalue==1) 0 else 1, position = left.position)
    }

    private fun logicalxor(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot compute $left locical-xor $right"
        return when {
            left.intvalue!=null -> when {
                right.intvalue!=null -> LiteralValue(
                        intvalue = if ((left.intvalue!=0).xor(right.intvalue!=0)) 1 else 0,
                        position = left.position)
                right.floatvalue!=null -> LiteralValue(
                        intvalue = if ((left.intvalue!=0).xor(right.floatvalue!=0.0)) 1 else 0,
                        position = left.position)
                else -> throw ExpressionException(error)
            }
            left.floatvalue!=null -> when {
                right.intvalue!=null -> LiteralValue(
                        intvalue = if ((left.floatvalue!=0.0).xor(right.intvalue!=0)) 1 else 0,
                        position = left.position)
                right.floatvalue!=null -> LiteralValue(
                        intvalue = if ((left.floatvalue!=0.0).xor(right.floatvalue!=0.0)) 1 else 0,
                        position = left.position)
                else -> throw ExpressionException(error)
            }
            else -> throw ExpressionException(error)
        }
    }

    private fun logicalor(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot compute $left locical-or $right"
        return when {
            left.intvalue!=null -> when {
                right.intvalue!=null -> LiteralValue(
                        intvalue = if (left.intvalue!=0 || right.intvalue!=0) 1 else 0,
                        position = left.position)
                right.floatvalue!=null -> LiteralValue(
                        intvalue = if (left.intvalue!=0 || right.floatvalue!=0.0) 1 else 0,
                        position = left.position)
                else -> throw ExpressionException(error)
            }
            left.floatvalue!=null -> when {
                right.intvalue!=null -> LiteralValue(
                        intvalue = if (left.floatvalue!=0.0 || right.intvalue!=0) 1 else 0,
                        position = left.position)
                right.floatvalue!=null -> LiteralValue(
                        intvalue = if (left.floatvalue!=0.0 || right.floatvalue!=0.0) 1 else 0,
                        position = left.position)
                else -> throw ExpressionException(error)
            }
            else -> throw ExpressionException(error)
        }
    }

    private fun logicaland(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot compute $left locical-and $right"
        return when {
            left.intvalue!=null -> when {
                right.intvalue!=null -> LiteralValue(
                        intvalue = if (left.intvalue!=0 && right.intvalue!=0) 1 else 0,
                        position = left.position)
                right.floatvalue!=null -> LiteralValue(
                        intvalue = if (left.intvalue!=0 && right.floatvalue!=0.0) 1 else 0,
                        position = left.position)
                else -> throw ExpressionException(error)
            }
            left.floatvalue!=null -> when {
                right.intvalue!=null -> LiteralValue(
                        intvalue = if (left.floatvalue!=0.0 && right.intvalue!=0) 1 else 0,
                        position = left.position)
                right.floatvalue!=null -> LiteralValue(
                        intvalue = if (left.floatvalue!=0.0 && right.floatvalue!=0.0) 1 else 0,
                        position = left.position)
                else -> throw ExpressionException(error)
            }
            else -> throw ExpressionException(error)
        }
    }

    private fun bitwisexor(left: LiteralValue, right: LiteralValue): LiteralValue {
        if(left.intvalue!=null && right.intvalue !=null)
            return LiteralValue(intvalue = left.intvalue.xor(right.intvalue), position = left.position)
        throw ExpressionException("cannot calculate $left ^ $right")
    }

    private fun bitwiseor(left: LiteralValue, right: LiteralValue): LiteralValue {
        if(left.intvalue!=null && right.intvalue !=null)
            return LiteralValue(intvalue = left.intvalue.or(right.intvalue), position = left.position)
        throw ExpressionException("cannot calculate $left | $right")
    }

    private fun bitwiseand(left: LiteralValue, right: LiteralValue): LiteralValue {
        if(left.intvalue!=null && right.intvalue !=null)
            return LiteralValue(intvalue = left.intvalue.and(right.intvalue), position = left.position)
        throw ExpressionException("cannot calculate $left & $right")
    }

    private fun rotateright(left: LiteralValue, right: LiteralValue): LiteralValue {
        throw ExpressionException("ror not possible on literal values")
    }

    private fun rotateleft(left: LiteralValue, right: LiteralValue): LiteralValue {
        throw ExpressionException("rol not possible on literal values")
    }

    private fun shiftright(left: LiteralValue, right: LiteralValue): LiteralValue {
        if(left.intvalue!=null && right.intvalue !=null)
            return LiteralValue(intvalue = left.intvalue.shr(right.intvalue), position = left.position)
        throw ExpressionException("cannot calculate $left >> $right")
    }

    private fun shiftleft(left: LiteralValue, right: LiteralValue): LiteralValue {
        if(left.intvalue!=null && right.intvalue !=null)
            return LiteralValue(intvalue = left.intvalue.shl(right.intvalue), position = left.position)
        throw ExpressionException("cannot calculate $left << $right")
    }

    private fun power(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot calculate $left ** $right"
        return when {
            left.intvalue!=null -> when {
                right.intvalue!=null -> LiteralValue(intvalue = left.intvalue.toDouble().pow(right.intvalue).toInt(), position = left.position)
                right.floatvalue!=null -> LiteralValue(floatvalue = left.intvalue.toDouble().pow(right.floatvalue), position = left.position)
                else -> throw ExpressionException(error)
            }
            left.floatvalue!=null -> when {
                right.intvalue!=null -> LiteralValue(floatvalue = left.floatvalue.pow(right.intvalue), position = left.position)
                right.floatvalue!=null -> LiteralValue(floatvalue = left.floatvalue.pow(right.floatvalue), position = left.position)
                else -> throw ExpressionException(error)
            }
            else -> throw ExpressionException(error)
        }
    }

    private fun plus(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot add $left and $right"
        return when {
            left.intvalue!=null -> when {
                right.intvalue!=null -> LiteralValue(intvalue = left.intvalue + right.intvalue, position = left.position)
                right.floatvalue!=null -> LiteralValue(floatvalue = left.intvalue + right.floatvalue, position = left.position)
                else -> throw ExpressionException(error)
            }
            left.floatvalue!=null -> when {
                right.intvalue!=null -> LiteralValue(floatvalue = left.floatvalue + right.intvalue, position = left.position)
                right.floatvalue!=null -> LiteralValue(floatvalue = left.floatvalue + right.floatvalue, position = left.position)
                else -> throw ExpressionException(error)
            }
            else -> throw ExpressionException(error)
        }
    }

    private fun minus(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot subtract $left and $right"
        return when {
            left.intvalue!=null -> when {
                right.intvalue!=null -> LiteralValue(intvalue = left.intvalue - right.intvalue, position = left.position)
                right.floatvalue!=null -> LiteralValue(floatvalue = left.intvalue - right.floatvalue, position = left.position)
                else -> throw ExpressionException(error)
            }
            left.floatvalue!=null -> when {
                right.intvalue!=null -> LiteralValue(floatvalue = left.floatvalue - right.intvalue, position = left.position)
                right.floatvalue!=null -> LiteralValue(floatvalue = left.floatvalue - right.floatvalue, position = left.position)
                else -> throw ExpressionException(error)
            }
            else -> throw ExpressionException(error)
        }
    }

    private fun multiply(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot multiply $left and $right"
        return when {
            left.intvalue!=null -> when {
                right.intvalue!=null -> LiteralValue(intvalue = left.intvalue * right.intvalue, position = left.position)
                right.floatvalue!=null -> LiteralValue(floatvalue = left.intvalue * right.floatvalue, position = left.position)
                right.strvalue!=null -> {
                    if(right.strvalue.length * left.intvalue > 65535) throw ExpressionException("string too large")
                    LiteralValue(strvalue = right.strvalue.repeat(left.intvalue), position = left.position)
                }
                else -> throw ExpressionException(error)
            }
            left.floatvalue!=null -> when {
                right.intvalue!=null -> LiteralValue(floatvalue = left.floatvalue * right.intvalue, position = left.position)
                right.floatvalue!=null -> LiteralValue(floatvalue = left.floatvalue * right.floatvalue, position = left.position)
                else -> throw ExpressionException(error)
            }
            left.strvalue!=null -> when {
                right.intvalue!=null -> {
                    if(left.strvalue.length * right.intvalue > 65535) throw ExpressionException("string too large")
                    LiteralValue(strvalue = left.strvalue.repeat(right.intvalue), position=left.position)
                }
                else -> throw ExpressionException(error)
            }
            else -> throw ExpressionException(error)
        }
    }

    private fun divide(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot divide $left by $right"
        return when {
            left.intvalue!=null -> when {
                right.intvalue!=null -> {
                    if(right.intvalue==0) throw ExpressionException("attempt to divide by zero")
                    LiteralValue(intvalue = left.intvalue / right.intvalue, position = left.position)
                }
                right.floatvalue!=null -> {
                    if(right.floatvalue==0.0) throw ExpressionException("attempt to divide by zero")
                    LiteralValue(floatvalue = left.intvalue / right.floatvalue, position = left.position)
                }
                else -> throw ExpressionException(error)
            }
            left.floatvalue!=null -> when {
                right.intvalue!=null -> {
                    if(right.intvalue==0) throw ExpressionException("attempt to divide by zero")
                    LiteralValue(floatvalue = left.floatvalue / right.intvalue, position = left.position)
                }
                right.floatvalue!=null -> {
                    if(right.floatvalue==0.0) throw ExpressionException("attempt to divide by zero")
                    LiteralValue(floatvalue = left.floatvalue / right.floatvalue, position = left.position)
                }
                else -> throw ExpressionException(error)
            }
            else -> throw ExpressionException(error)
        }
    }
}
