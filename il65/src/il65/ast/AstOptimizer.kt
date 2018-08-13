package il65.ast

import kotlin.math.pow


fun Module.optimize() {
    val optimizer = AstOptimizer()
    this.process(optimizer)
    if(optimizer.optimizationsDone==0)
        println("[${this.name}] 0 optimizations performed")

    while(optimizer.optimizationsDone>0) {
        println("[${this.name}] ${optimizer.optimizationsDone} optimizations performed")
        optimizer.reset()
        this.process(optimizer)
    }
}


class AstOptimizer : IAstProcessor {
    var optimizationsDone: Int = 0
        private set

    fun reset() {
        optimizationsDone = 0
    }

    override fun process(module: Module) {
        module.lines = module.lines.map { it.process(this) }
    }

    override fun process(block: Block): IStatement {
        block.statements = block.statements.map { it.process(this) }
        return block
    }

    override fun process(subroutine: Subroutine): IStatement {
        subroutine.statements = subroutine.statements.map { it.process(this) }
        return subroutine
    }

    override fun process(functionCall: FunctionCall): IExpression {
        functionCall.arglist = functionCall.arglist.map{it.process(this)}
        return functionCall
    }

    override fun process(decl: VarDecl): IStatement {
        decl.value = decl.value?.process(this)
        decl.arrayspec?.process(this)
        return decl
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
            val result = when {
                expr.operator == "+" -> subexpr
                expr.operator == "-" -> when {
                    subexpr.intvalue != null -> {
                        optimizationsDone++
                        LiteralValue(intvalue = subexpr.intvalue)
                    }
                    subexpr.floatvalue != null -> {
                        optimizationsDone++
                        LiteralValue(floatvalue = -subexpr.floatvalue)
                    }
                    else -> throw UnsupportedOperationException("can only take negative of int or float")
                }
                expr.operator == "~" -> when {
                    subexpr.intvalue != null -> {
                        optimizationsDone++
                        LiteralValue(intvalue = subexpr.intvalue.inv())
                    }
                    else -> throw UnsupportedOperationException("can only take bitwise inversion of int")
                }
                expr.operator == "not" -> when {
                    subexpr.intvalue != null -> {
                        optimizationsDone++
                        LiteralValue(intvalue = if (subexpr.intvalue == 0) 1 else 0)
                    }
                    subexpr.floatvalue != null -> {
                        optimizationsDone++
                        LiteralValue(intvalue = if (subexpr.floatvalue == 0.0) 1 else 0)
                    }
                    else -> throw UnsupportedOperationException("can not take logical not of $subexpr")
                }
                else -> throw UnsupportedOperationException(expr.operator)
            }
            result.position = subexpr.position
            return result
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
                optimizationsDone++
                evaluator.evaluate(leftconst, expr.operator, rightconst)
            }
            else -> expr
        }
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
        val litval = LiteralValue(intvalue = if(leq.intvalue==1) 0 else 1)
        litval.position = left.position
        return litval
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
        val litval = LiteralValue(intvalue = if(leftvalue==rightvalue) 1 else 0)
        litval.position = left.position
        return litval
    }

    private fun comparegreaterequal(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot compute $left >= $right"
        val litval = when {
            left.intvalue!=null -> when {
                right.intvalue!=null -> LiteralValue(
                        intvalue = if (left.intvalue >= right.intvalue) 1 else 0)
                right.floatvalue!=null -> LiteralValue(
                        intvalue = if (left.intvalue >= right.floatvalue) 1 else 0)
                else -> throw ExpressionException(error)
            }
            left.floatvalue!=null -> when {
                right.intvalue!=null -> LiteralValue(
                        intvalue = if (left.floatvalue >= right.intvalue) 1 else 0)
                right.floatvalue!=null -> LiteralValue(
                        intvalue = if (left.floatvalue >= right.floatvalue) 1 else 0)
                else -> throw ExpressionException(error)
            }
            else -> throw ExpressionException(error)
        }
        litval.position = left.position
        return litval
    }

    private fun comparelessequal(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot compute $left >= $right"
        val litval = when {
            left.intvalue!=null -> when {
                right.intvalue!=null -> LiteralValue(
                        intvalue = if (left.intvalue <= right.intvalue) 1 else 0)
                right.floatvalue!=null -> LiteralValue(
                        intvalue = if (left.intvalue <= right.floatvalue) 1 else 0)
                else -> throw ExpressionException(error)
            }
            left.floatvalue!=null -> when {
                right.intvalue!=null -> LiteralValue(
                        intvalue = if (left.floatvalue <= right.intvalue) 1 else 0)
                right.floatvalue!=null -> LiteralValue(
                        intvalue = if (left.floatvalue <= right.floatvalue) 1 else 0)
                else -> throw ExpressionException(error)
            }
            else -> throw ExpressionException(error)
        }
        litval.position = left.position
        return litval
    }

    private fun comparegreater(left: LiteralValue, right: LiteralValue): LiteralValue {
        val leq = comparelessequal(left, right)
        val litval = LiteralValue(intvalue = if(leq.intvalue==1) 0 else 1)
        litval.position = left.position
        return litval
    }

    private fun compareless(left: LiteralValue, right: LiteralValue): LiteralValue {
        val leq = comparegreaterequal(left, right)
        val litval = LiteralValue(intvalue = if(leq.intvalue==1) 0 else 1)
        litval.position = left.position
        return litval
    }

    private fun logicalxor(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot compute $left locical-xor $right"
        val litval = when {
            left.intvalue!=null -> when {
                right.intvalue!=null -> LiteralValue(
                        intvalue = if ((left.intvalue!=0).xor(right.intvalue!=0)) 1 else 0)
                right.floatvalue!=null -> LiteralValue(
                        intvalue = if ((left.intvalue!=0).xor(right.floatvalue!=0.0)) 1 else 0)
                else -> throw ExpressionException(error)
            }
            left.floatvalue!=null -> when {
                right.intvalue!=null -> LiteralValue(
                        intvalue = if ((left.floatvalue!=0.0).xor(right.intvalue!=0)) 1 else 0)
                right.floatvalue!=null -> LiteralValue(
                        intvalue = if ((left.floatvalue!=0.0).xor(right.floatvalue!=0.0)) 1 else 0)
                else -> throw ExpressionException(error)
            }
            else -> throw ExpressionException(error)
        }
        litval.position = left.position
        return litval
    }

    private fun logicalor(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot compute $left locical-or $right"
        val litval = when {
            left.intvalue!=null -> when {
                right.intvalue!=null -> LiteralValue(
                        intvalue = if (left.intvalue!=0 || right.intvalue!=0) 1 else 0)
                right.floatvalue!=null -> LiteralValue(
                        intvalue = if (left.intvalue!=0 || right.floatvalue!=0.0) 1 else 0)
                else -> throw ExpressionException(error)
            }
            left.floatvalue!=null -> when {
                right.intvalue!=null -> LiteralValue(
                        intvalue = if (left.floatvalue!=0.0 || right.intvalue!=0) 1 else 0)
                right.floatvalue!=null -> LiteralValue(
                        intvalue = if (left.floatvalue!=0.0 || right.floatvalue!=0.0) 1 else 0)
                else -> throw ExpressionException(error)
            }
            else -> throw ExpressionException(error)
        }
        litval.position = left.position
        return litval
    }

    private fun logicaland(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot compute $left locical-and $right"
        val litval = when {
            left.intvalue!=null -> when {
                right.intvalue!=null -> LiteralValue(
                        intvalue = if (left.intvalue!=0 && right.intvalue!=0) 1 else 0)
                right.floatvalue!=null -> LiteralValue(
                        intvalue = if (left.intvalue!=0 && right.floatvalue!=0.0) 1 else 0)
                else -> throw ExpressionException(error)
            }
            left.floatvalue!=null -> when {
                right.intvalue!=null -> LiteralValue(
                        intvalue = if (left.floatvalue!=0.0 && right.intvalue!=0) 1 else 0)
                right.floatvalue!=null -> LiteralValue(
                        intvalue = if (left.floatvalue!=0.0 && right.floatvalue!=0.0) 1 else 0)
                else -> throw ExpressionException(error)
            }
            else -> throw ExpressionException(error)
        }
        litval.position = left.position
        return litval
    }

    private fun bitwisexor(left: LiteralValue, right: LiteralValue): LiteralValue {
        if(left.intvalue!=null && right.intvalue !=null) {
            val litval = LiteralValue(intvalue = left.intvalue.xor(right.intvalue))
            litval.position = left.position
            return litval
        }
        throw ExpressionException("cannot calculate $left ^ $right")
    }

    private fun bitwiseor(left: LiteralValue, right: LiteralValue): LiteralValue {
        if(left.intvalue!=null && right.intvalue !=null) {
            val litval = LiteralValue(intvalue = left.intvalue.or(right.intvalue))
            litval.position = left.position
            return litval
        }
        throw ExpressionException("cannot calculate $left | $right")
    }

    private fun bitwiseand(left: LiteralValue, right: LiteralValue): LiteralValue {
        if(left.intvalue!=null && right.intvalue !=null) {
            val litval = LiteralValue(intvalue = left.intvalue.and(right.intvalue))
            litval.position = left.position
            return litval
        }
        throw ExpressionException("cannot calculate $left & $right")
    }

    private fun rotateright(left: LiteralValue, right: LiteralValue): LiteralValue {
        throw ExpressionException("ror not possible on literal values")
    }

    private fun rotateleft(left: LiteralValue, right: LiteralValue): LiteralValue {
        throw ExpressionException("rol not possible on literal values")
    }

    private fun shiftright(left: LiteralValue, right: LiteralValue): LiteralValue {
        if(left.intvalue!=null && right.intvalue !=null) {
            val litval = LiteralValue(intvalue = left.intvalue.shr(right.intvalue))
            litval.position = left.position
            return litval
        }
        throw ExpressionException("cannot calculate $left >> $right")
    }

    private fun shiftleft(left: LiteralValue, right: LiteralValue): LiteralValue {
        if(left.intvalue!=null && right.intvalue !=null) {
            val litval = LiteralValue(intvalue = left.intvalue.shl(right.intvalue))
            litval.position = left.position
            return litval
        }
        throw ExpressionException("cannot calculate $left << $right")
    }

    private fun power(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot calculate $left ** $right"
        val litval = when {
            left.intvalue!=null -> when {
                right.intvalue!=null -> LiteralValue(intvalue = left.intvalue.toDouble().pow(right.intvalue).toInt())
                right.floatvalue!=null -> LiteralValue(floatvalue = left.intvalue.toDouble().pow(right.floatvalue))
                else -> throw ExpressionException(error)
            }
            left.floatvalue!=null -> when {
                right.intvalue!=null -> LiteralValue(floatvalue = left.floatvalue.pow(right.intvalue))
                right.floatvalue!=null -> LiteralValue(floatvalue = left.floatvalue.pow(right.floatvalue))
                else -> throw ExpressionException(error)
            }
            else -> throw ExpressionException(error)
        }
        litval.position = left.position
        return litval
    }

    private fun plus(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot add $left and $right"
        val litval = when {
            left.intvalue!=null -> when {
                right.intvalue!=null -> LiteralValue(intvalue = left.intvalue + right.intvalue)
                right.floatvalue!=null -> LiteralValue(floatvalue = left.intvalue + right.floatvalue)
                else -> throw ExpressionException(error)
            }
            left.floatvalue!=null -> when {
                right.intvalue!=null -> LiteralValue(floatvalue = left.floatvalue + right.intvalue)
                right.floatvalue!=null -> LiteralValue(floatvalue = left.floatvalue + right.floatvalue)
                else -> throw ExpressionException(error)
            }
            else -> throw ExpressionException(error)
        }
        litval.position = left.position
        return litval
    }

    private fun minus(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot subtract $left and $right"
        val litval = when {
            left.intvalue!=null -> when {
                right.intvalue!=null -> LiteralValue(intvalue = left.intvalue - right.intvalue)
                right.floatvalue!=null -> LiteralValue(floatvalue = left.intvalue - right.floatvalue)
                else -> throw ExpressionException(error)
            }
            left.floatvalue!=null -> when {
                right.intvalue!=null -> LiteralValue(floatvalue = left.floatvalue - right.intvalue)
                right.floatvalue!=null -> LiteralValue(floatvalue = left.floatvalue - right.floatvalue)
                else -> throw ExpressionException(error)
            }
            else -> throw ExpressionException(error)
        }
        litval.position = left.position
        return litval
    }

    private fun multiply(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot multiply $left and $right"
        val litval = when {
            left.intvalue!=null -> when {
                right.intvalue!=null -> LiteralValue(intvalue = left.intvalue * right.intvalue)
                right.floatvalue!=null -> LiteralValue(floatvalue = left.intvalue * right.floatvalue)
                right.strvalue!=null -> {
                    if(right.strvalue.length * left.intvalue > 65535) throw ExpressionException("string too large")
                    LiteralValue(strvalue = right.strvalue.repeat(left.intvalue))
                }
                else -> throw ExpressionException(error)
            }
            left.floatvalue!=null -> when {
                right.intvalue!=null -> LiteralValue(floatvalue = left.floatvalue * right.intvalue)
                right.floatvalue!=null -> LiteralValue(floatvalue = left.floatvalue * right.floatvalue)
                else -> throw ExpressionException(error)
            }
            left.strvalue!=null -> when {
                right.intvalue!=null -> {
                    if(left.strvalue.length * right.intvalue > 65535) throw ExpressionException("string too large")
                    LiteralValue(strvalue = left.strvalue.repeat(right.intvalue))
                }
                else -> throw ExpressionException(error)
            }
            else -> throw ExpressionException(error)
        }
        litval.position = left.position
        return litval
    }

    private fun divide(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot divide $left by $right"
        val litval = when {
            left.intvalue!=null -> when {
                right.intvalue!=null -> {
                    if(right.intvalue==0) throw ExpressionException("attempt to divide by zero")
                    LiteralValue(intvalue = left.intvalue / right.intvalue)
                }
                right.floatvalue!=null -> {
                    if(right.floatvalue==0.0) throw ExpressionException("attempt to divide by zero")
                    LiteralValue(floatvalue = left.intvalue / right.floatvalue)
                }
                else -> throw ExpressionException(error)
            }
            left.floatvalue!=null -> when {
                right.intvalue!=null -> {
                    if(right.intvalue==0) throw ExpressionException("attempt to divide by zero")
                    LiteralValue(floatvalue = left.floatvalue / right.intvalue)
                }
                right.floatvalue!=null -> {
                    if(right.floatvalue==0.0) throw ExpressionException("attempt to divide by zero")
                    LiteralValue(floatvalue = left.floatvalue / right.floatvalue)
                }
                else -> throw ExpressionException(error)
            }
            else -> throw ExpressionException(error)
        }
        litval.position = left.position
        return litval
    }
}
