package il65.optimizing

import il65.parser.ParsingFailedError
import il65.ast.*
import il65.compiler.Petscii
import kotlin.math.pow


fun Module.optimizeExpressions(globalNamespace: INameScope) {
    val optimizer = ExpressionOptimizer(globalNamespace)
    try {
        this.process(optimizer)
    } catch (ax: AstException) {
        optimizer.addError(ax)
    }

    if(optimizer.optimizationsDone==0)
        println("[${this.name}] 0 expression optimizations performed")

    while(optimizer.errors.isEmpty() && optimizer.optimizationsDone>0) {
        println("[${this.name}] ${optimizer.optimizationsDone} expression optimizations performed")
        optimizer.optimizationsDone = 0
        this.process(optimizer)
    }

    if(optimizer.errors.isNotEmpty()) {
        optimizer.errors.forEach { System.err.println(it) }
        throw ParsingFailedError("There are ${optimizer.errors.size} errors.")
    } else {
        this.linkParents()  // re-link in final configuration
    }
}



/*
    todo eliminate useless terms:
        *0 -> constant 0
        X*1, X/1, X//1 -> just X
        X*-1 -> unary prefix -X
        X**0 -> 1
        X**1 -> X
        X**-1 -> 1.0/X
        X << 0 -> X
        X | 0 -> X
        x & 0 -> 0
        X ^ 0 -> X

    todo expression optimization: reduce expression nesting / flattening of parenthesis
    todo expression optimization: simplify logical expression when a term makes it always true or false (1 or 0)
    todo expression optimization: optimize some simple multiplications into shifts  (A*8 -> A<<3,  A/4 -> A>>2)
    todo expression optimization: common (sub) expression elimination (turn common expressions into single subroutine call)

 */
class ExpressionOptimizer(private val globalNamespace: INameScope) : IAstProcessor {
    var optimizationsDone: Int = 0
    var errors : MutableList<AstException> = mutableListOf()

    private val reportedErrorMessages = mutableSetOf<String>()

    fun addError(x: AstException) {
        // check that we don't add the same error more than once
        if(!reportedErrorMessages.contains(x.toString())) {
            reportedErrorMessages.add(x.toString())
            errors.add(x)
        }
    }

    override fun process(decl: VarDecl): IStatement {
        // the initializer value can't refer to the variable itself (recursive definition)
        if(decl.value?.referencesIdentifier(decl.name) == true||
                decl.arrayspec?.x?.referencesIdentifier(decl.name) == true ||
                decl.arrayspec?.y?.referencesIdentifier(decl.name) == true) {
            errors.add(ExpressionError("recursive var declaration", decl.position))
            return decl
        }

        val result = super.process(decl)

        if(decl.type==VarDeclType.CONST || decl.type==VarDeclType.VAR) {
            when {
                decl.datatype == DataType.FLOAT -> {
                    // vardecl: for float vars, promote constant integer initialization values to floats
                    val literal = decl.value as? LiteralValue
                    if (literal != null && literal.isInteger) {
                        val newValue = LiteralValue(floatvalue = literal.asNumericValue!!.toDouble())
                        newValue.position = literal.position
                        decl.value = newValue
                    }
                }
                decl.datatype == DataType.BYTE || decl.datatype == DataType.WORD -> {
                    // vardecl: for byte/word vars, convert char/string of length 1 initialization values to integer
                    val literal = decl.value as? LiteralValue
                    if (literal != null && literal.isString && literal.strvalue?.length == 1) {
                        val petscii = Petscii.encodePetscii(literal.strvalue)[0]
                        val newValue = LiteralValue(bytevalue = petscii)
                        newValue.position = literal.position
                        decl.value = newValue
                    }
                }
            }
        }
        return result
    }

    /**
     * replace identifiers that refer to const value, with the value itself
     */
    override fun process(identifier: IdentifierReference): IExpression {
        return try {
            identifier.constValue(globalNamespace) ?: identifier
        } catch (ax: AstException) {
            addError(ax)
            identifier
        }
    }

    override fun process(functionCall: FunctionCall): IExpression {
        return try {
            super.process(functionCall)
            functionCall.constValue(globalNamespace) ?: functionCall
        } catch (ax: AstException) {
            addError(ax)
            functionCall
        }
    }

    /**
     * Try to process a unary prefix expression.
     * Compile-time constant sub expressions will be evaluated on the spot.
     * For instance, the expression for "- 4.5" will be optimized into the float literal -4.5
     */
    override fun process(expr: PrefixExpression): IExpression {
        return try {
            super.process(expr)

            val subexpr = expr.expression
            if (subexpr is LiteralValue) {
                // process prefixed literal values (such as -3, not true)
                val result = when {
                    expr.operator == "+" -> subexpr
                    expr.operator == "-" -> when {
                        subexpr.asIntegerValue!= null -> {
                            optimizationsDone++
                            LiteralValue.optimalNumeric(-subexpr.asIntegerValue)
                        }
                        subexpr.floatvalue != null -> {
                            optimizationsDone++
                            LiteralValue(floatvalue = -subexpr.floatvalue)
                        }
                        else -> throw ExpressionError("can only take negative of int or float", subexpr.position)
                    }
                    expr.operator == "~" -> when {
                        subexpr.asIntegerValue != null -> {
                            optimizationsDone++
                            LiteralValue.optimalNumeric(subexpr.asIntegerValue.inv())
                        }
                        else -> throw ExpressionError("can only take bitwise inversion of int", subexpr.position)
                    }
                    expr.operator == "not" -> when {
                        subexpr.asIntegerValue != null -> {
                            optimizationsDone++
                            LiteralValue(bytevalue = if (subexpr.asIntegerValue == 0) 1 else 0)
                        }
                        subexpr.floatvalue != null -> {
                            optimizationsDone++
                            LiteralValue(bytevalue = if (subexpr.floatvalue == 0.0) 1 else 0)
                        }
                        else -> throw ExpressionError("can not take logical not of $subexpr", subexpr.position)
                    }
                    else -> throw ExpressionError(expr.operator, subexpr.position)
                }
                result.position = subexpr.position
                return result
            }
            return expr
        } catch (ax: AstException) {
            addError(ax)
            expr
        }
    }

    /**
     * Try to process a binary expression.
     * Compile-time constant sub expressions will be evaluated on the spot.
     * For instance, "9 * (4 + 2)" will be optimized into the integer literal 54.
     */
    override fun process(expr: BinaryExpression): IExpression {
        return try {
            super.process(expr)

            val evaluator = ConstExprEvaluator()
            val leftconst = expr.left.constValue(globalNamespace)
            val rightconst = expr.right.constValue(globalNamespace)
            return when {
                leftconst != null && rightconst != null -> {
                    optimizationsDone++
                    evaluator.evaluate(leftconst, expr.operator, rightconst)
                }
                else -> expr
            }
        } catch (ax: AstException) {
            addError(ax)
            expr
        }
    }

    override fun process(range: RangeExpr): IExpression {
        return try {
            super.process(range)
            val from = range.from.constValue(globalNamespace)
            val to = range.to.constValue(globalNamespace)
            if (from != null && to != null) {
                when {
                    from.isWord || to.isWord -> {
                        // range on word value boundaries
                        val rangeValue = from.asIntegerValue!!.rangeTo(to.asIntegerValue!!)
                        if (rangeValue.last - rangeValue.first > 65535) {
                            throw ExpressionError("amount of values in range exceeds 65535", range.position)
                        }
                        return LiteralValue(arrayvalue = rangeValue.map {
                            val v = LiteralValue(wordvalue = it)
                            v.position = range.position
                            v.parent = range.parent
                            v
                        }.toMutableList())
                    }
                    from.isByte && to.isByte -> {
                        // range on byte value  boundaries
                        val rangeValue = from.bytevalue!!.rangeTo(to.bytevalue!!)
                        if (rangeValue.last - rangeValue.first > 65535) {
                            throw ExpressionError("amount of values in range exceeds 65535", range.position)
                        }
                        return LiteralValue(arrayvalue = rangeValue.map {
                            val v = LiteralValue(bytevalue = it.toShort())
                            v.position = range.position
                            v.parent = range.parent
                            v
                        }.toMutableList())
                    }
                    from.strvalue != null && to.strvalue != null -> {
                        // char range
                        val rangevalue = from.strvalue[0].rangeTo(to.strvalue[0])
                        if (rangevalue.last - rangevalue.first > 65535) {
                            throw ExpressionError("amount of characters in range exceeds 65535", range.position)
                        }
                        val newval = LiteralValue(strvalue = rangevalue.toList().joinToString(""))
                        newval.position = range.position
                        newval.parent = range.parent
                        return newval
                    }
                    else -> AstException("range on weird datatype")
                }
            }
            return range
        } catch (ax: AstException) {
            addError(ax)
            range
        }
    }

    override fun process(literalValue: LiteralValue): LiteralValue {
        if(literalValue.arrayvalue!=null) {
            val newArray = literalValue.arrayvalue.map { it.process(this) }
            literalValue.arrayvalue.clear()
            literalValue.arrayvalue.addAll(newArray)
        }
        return super.process(literalValue)
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
            else -> throw FatalAstException("const evaluation for invalid operator $operator")
        }
    }

    private fun comparenotequal(left: LiteralValue, right: LiteralValue): LiteralValue {
        val leq = compareequal(left, right)
        val litval = LiteralValue(bytevalue = if (leq.bytevalue == 1.toShort()) 0 else 1)
        litval.position = left.position
        return litval
    }

    private fun compareequal(left: LiteralValue, right: LiteralValue): LiteralValue {
        val leftvalue: Any = when {
            left.bytevalue!=null -> left.bytevalue
            left.wordvalue!=null -> left.wordvalue
            left.floatvalue!=null -> left.floatvalue
            left.strvalue!=null -> left.strvalue
            left.arrayvalue!=null -> left.arrayvalue
            else -> throw FatalAstException("missing literal value")
        }
        val rightvalue: Any = when {
            right.bytevalue!=null -> right.bytevalue
            right.wordvalue!=null -> right.wordvalue
            right.floatvalue!=null -> right.floatvalue
            right.strvalue!=null -> right.strvalue
            right.arrayvalue!=null -> right.arrayvalue
            else -> throw FatalAstException("missing literal value")
        }
        val litval = LiteralValue(bytevalue = if (leftvalue == rightvalue) 1 else 0)
        litval.position = left.position
        return litval
    }

    private fun comparegreaterequal(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot compute $left >= $right"
        val litval = when {
            left.asIntegerValue!=null -> when {
                right.asIntegerValue!=null -> LiteralValue(
                        bytevalue = if (left.asIntegerValue >= right.asIntegerValue) 1 else 0)
                right.floatvalue!=null -> LiteralValue(
                        bytevalue = if (left.asIntegerValue >= right.floatvalue) 1 else 0)
                else -> throw ExpressionError(error, left.position)
            }
            left.floatvalue!=null -> when {
                right.asIntegerValue!=null -> LiteralValue(
                        bytevalue = if (left.floatvalue >= right.asIntegerValue) 1 else 0)
                right.floatvalue!=null -> LiteralValue(
                        bytevalue = if (left.floatvalue >= right.floatvalue) 1 else 0)
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
        litval.position = left.position
        return litval
    }

    private fun comparelessequal(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot compute $left >= $right"
        val litval = when {
            left.asIntegerValue!=null -> when {
                right.asIntegerValue!=null -> LiteralValue(
                        bytevalue = if (left.asIntegerValue <= right.asIntegerValue) 1 else 0)
                right.floatvalue!=null -> LiteralValue(
                        bytevalue = if (left.asIntegerValue <= right.floatvalue) 1 else 0)
                else -> throw ExpressionError(error, left.position)
            }
            left.floatvalue!=null -> when {
                right.asIntegerValue!=null -> LiteralValue(
                        bytevalue = if (left.floatvalue <= right.asIntegerValue) 1 else 0)
                right.floatvalue!=null -> LiteralValue(
                        bytevalue = if (left.floatvalue <= right.floatvalue) 1 else 0)
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
        litval.position = left.position
        return litval
    }

    private fun comparegreater(left: LiteralValue, right: LiteralValue): LiteralValue {
        val leq = comparelessequal(left, right)
        val litval = LiteralValue(bytevalue = if (leq.bytevalue == 1.toShort()) 0 else 1)
        litval.position = left.position
        return litval
    }

    private fun compareless(left: LiteralValue, right: LiteralValue): LiteralValue {
        val leq = comparegreaterequal(left, right)
        val litval = LiteralValue(bytevalue = if (leq.bytevalue == 1.toShort()) 0 else 1)
        litval.position = left.position
        return litval
    }

    private fun logicalxor(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot compute $left locical-bitxor $right"
        val litval = when {
            left.asIntegerValue!=null -> when {
                right.asIntegerValue!=null -> LiteralValue(
                        bytevalue = if ((left.asIntegerValue != 0).xor(right.asIntegerValue != 0)) 1 else 0)
                right.floatvalue!=null -> LiteralValue(
                        bytevalue = if ((left.asIntegerValue != 0).xor(right.floatvalue != 0.0)) 1 else 0)
                else -> throw ExpressionError(error, left.position)
            }
            left.floatvalue!=null -> when {
                right.asIntegerValue!=null -> LiteralValue(
                        bytevalue = if ((left.floatvalue != 0.0).xor(right.asIntegerValue != 0)) 1 else 0)
                right.floatvalue!=null -> LiteralValue(
                        bytevalue = if ((left.floatvalue != 0.0).xor(right.floatvalue != 0.0)) 1 else 0)
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
        litval.position = left.position
        return litval
    }

    private fun logicalor(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot compute $left locical-or $right"
        val litval = when {
            left.asIntegerValue!=null -> when {
                right.asIntegerValue!=null -> LiteralValue(
                        bytevalue = if (left.asIntegerValue != 0 || right.asIntegerValue != 0) 1 else 0)
                right.floatvalue!=null -> LiteralValue(
                        bytevalue = if (left.asIntegerValue != 0 || right.floatvalue != 0.0) 1 else 0)
                else -> throw ExpressionError(error, left.position)
            }
            left.floatvalue!=null -> when {
                right.asIntegerValue!=null -> LiteralValue(
                        bytevalue = if (left.floatvalue != 0.0 || right.asIntegerValue != 0) 1 else 0)
                right.floatvalue!=null -> LiteralValue(
                        bytevalue = if (left.floatvalue != 0.0 || right.floatvalue != 0.0) 1 else 0)
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
        litval.position = left.position
        return litval
    }

    private fun logicaland(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot compute $left locical-and $right"
        val litval = when {
            left.asIntegerValue!=null -> when {
                right.asIntegerValue!=null -> LiteralValue(
                        bytevalue = if (left.asIntegerValue != 0 && right.asIntegerValue != 0) 1 else 0)
                right.floatvalue!=null -> LiteralValue(
                        bytevalue = if (left.asIntegerValue != 0 && right.floatvalue != 0.0) 1 else 0)
                else -> throw ExpressionError(error, left.position)
            }
            left.floatvalue!=null -> when {
                right.asIntegerValue!=null -> LiteralValue(
                        bytevalue = if (left.floatvalue != 0.0 && right.asIntegerValue != 0) 1 else 0)
                right.floatvalue!=null -> LiteralValue(
                        bytevalue = if (left.floatvalue != 0.0 && right.floatvalue != 0.0) 1 else 0)
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
        litval.position = left.position
        return litval
    }

    private fun bitwisexor(left: LiteralValue, right: LiteralValue): LiteralValue {
        if(left.isByte) {
            if(right.asIntegerValue!=null) {
                val litval = LiteralValue(bytevalue = (left.bytevalue!!.toInt() xor (right.asIntegerValue and 255)).toShort())
                litval.position = left.position
                return litval
            }
        } else if(left.isWord) {
            if(right.asIntegerValue!=null) {
                val litval = LiteralValue(wordvalue = left.wordvalue!! xor right.asIntegerValue)
                litval.position = left.position
                return litval
            }
        }
        throw ExpressionError("cannot calculate $left ^ $right", left.position)
    }

    private fun bitwiseor(left: LiteralValue, right: LiteralValue): LiteralValue {
        if(left.isByte) {
            if(right.asIntegerValue!=null) {
                val litval = LiteralValue(bytevalue = (left.bytevalue!!.toInt() or (right.asIntegerValue and 255)).toShort())
                litval.position = left.position
                return litval
            }
        } else if(left.isWord) {
            if(right.asIntegerValue!=null) {
                val litval = LiteralValue(wordvalue = left.wordvalue!! or right.asIntegerValue)
                litval.position = left.position
                return litval
            }
        }
        throw ExpressionError("cannot calculate $left | $right", left.position)
    }

    private fun bitwiseand(left: LiteralValue, right: LiteralValue): LiteralValue {
        if(left.isByte) {
            if(right.asIntegerValue!=null) {
                val litval = LiteralValue(bytevalue = (left.bytevalue!!.toInt() or (right.asIntegerValue and 255)).toShort())
                litval.position = left.position
                return litval
            }
        } else if(left.isWord) {
            if(right.asIntegerValue!=null) {
                val litval = LiteralValue(wordvalue = left.wordvalue!! or right.asIntegerValue)
                litval.position = left.position
                return litval
            }
        }
        throw ExpressionError("cannot calculate $left & $right", left.position)
    }

    private fun power(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot calculate $left ** $right"
        val litval = when {
            left.asIntegerValue!=null -> when {
                right.asIntegerValue!=null -> LiteralValue(wordvalue = left.asIntegerValue.toDouble().pow(right.asIntegerValue).toInt())
                right.floatvalue!=null -> LiteralValue(floatvalue = left.asIntegerValue.toDouble().pow(right.floatvalue))
                else -> throw ExpressionError(error, left.position)
            }
            left.floatvalue!=null -> when {
                right.asIntegerValue!=null -> LiteralValue(floatvalue = left.floatvalue.pow(right.asIntegerValue))
                right.floatvalue!=null -> LiteralValue(floatvalue = left.floatvalue.pow(right.floatvalue))
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
        litval.position = left.position
        return litval
    }

    private fun plus(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot add $left and $right"
        val litval = when {
            left.asIntegerValue!=null -> when {
                right.asIntegerValue!=null -> LiteralValue.optimalNumeric(left.asIntegerValue + right.asIntegerValue)
                right.floatvalue!=null -> LiteralValue(floatvalue = left.asIntegerValue + right.floatvalue)
                else -> throw ExpressionError(error, left.position)
            }
            left.floatvalue!=null -> when {
                right.asIntegerValue!=null -> LiteralValue(floatvalue = left.floatvalue + right.asIntegerValue)
                right.floatvalue!=null -> LiteralValue(floatvalue = left.floatvalue + right.floatvalue)
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
        litval.position = left.position
        return litval
    }

    private fun minus(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot subtract $left and $right"
        val litval = when {
            left.asIntegerValue!=null -> when {
                right.asIntegerValue!=null -> LiteralValue.optimalNumeric(left.asIntegerValue - right.asIntegerValue)
                right.floatvalue!=null -> LiteralValue(floatvalue = left.asIntegerValue - right.floatvalue)
                else -> throw ExpressionError(error, left.position)
            }
            left.floatvalue!=null -> when {
                right.asIntegerValue!=null -> LiteralValue(floatvalue = left.floatvalue - right.asIntegerValue)
                right.floatvalue!=null -> LiteralValue(floatvalue = left.floatvalue - right.floatvalue)
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
        litval.position = left.position
        return litval
    }

    private fun multiply(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot multiply $left and $right"
        val litval = when {
            left.asIntegerValue!=null -> when {
                right.asIntegerValue!=null -> LiteralValue.optimalNumeric(left.asIntegerValue * right.asIntegerValue)
                right.floatvalue!=null -> LiteralValue(floatvalue = left.asIntegerValue * right.floatvalue)
                right.strvalue!=null -> {
                    if(right.strvalue.length * left.asIntegerValue > 65535) throw ExpressionError("string too large", left.position)
                    LiteralValue(strvalue = right.strvalue.repeat(left.asIntegerValue))
                }
                else -> throw ExpressionError(error, left.position)
            }
            left.floatvalue!=null -> when {
                right.asIntegerValue!=null -> LiteralValue(floatvalue = left.floatvalue * right.asIntegerValue)
                right.floatvalue!=null -> LiteralValue(floatvalue = left.floatvalue * right.floatvalue)
                else -> throw ExpressionError(error, left.position)
            }
            left.strvalue!=null -> when {
                right.asIntegerValue!=null -> {
                    if(left.strvalue.length * right.asIntegerValue > 65535) throw ExpressionError("string too large", left.position)
                    LiteralValue(strvalue = left.strvalue.repeat(right.asIntegerValue))
                }
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
        litval.position = left.position
        return litval
    }

    private fun divide(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot divide $left by $right"
        val litval = when {
            left.asIntegerValue!=null -> when {
                right.asIntegerValue!=null -> {
                    if(right.asIntegerValue==0) throw ExpressionError("attempt to divide by zero", left.position)
                    LiteralValue.optimalNumeric(left.asIntegerValue / right.asIntegerValue)
                }
                right.floatvalue!=null -> {
                    if(right.floatvalue==0.0) throw ExpressionError("attempt to divide by zero", left.position)
                    LiteralValue(floatvalue = left.asIntegerValue / right.floatvalue)
                }
                else -> throw ExpressionError(error, left.position)
            }
            left.floatvalue!=null -> when {
                right.asIntegerValue!=null -> {
                    if(right.asIntegerValue==0) throw ExpressionError("attempt to divide by zero", left.position)
                    LiteralValue(floatvalue = left.floatvalue / right.asIntegerValue)
                }
                right.floatvalue!=null -> {
                    if(right.floatvalue==0.0) throw ExpressionError("attempt to divide by zero", left.position)
                    LiteralValue(floatvalue = left.floatvalue / right.floatvalue)
                }
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
        litval.position = left.position
        return litval
    }
}
