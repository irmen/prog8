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

    todo expression optimization: remove redundant builtin function calls
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
                    if (literal != null && (literal.type == DataType.BYTE || literal.type==DataType.WORD)) {
                        val newValue = LiteralValue(DataType.FLOAT, floatvalue = literal.asNumericValue!!.toDouble(), position = literal.position)
                        decl.value = newValue
                    }
                }
                decl.datatype == DataType.BYTE || decl.datatype == DataType.WORD -> {
                    // vardecl: for byte/word vars, convert char/string of length 1 initialization values to integer
                    val literal = decl.value as? LiteralValue
                    if (literal != null && literal.isString && literal.strvalue?.length == 1) {
                        val petscii = Petscii.encodePetscii(literal.strvalue)[0]
                        val newValue = LiteralValue(DataType.BYTE, bytevalue = petscii, position = literal.position)
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
                return when {
                    expr.operator == "+" -> subexpr
                    expr.operator == "-" -> when {
                        subexpr.asIntegerValue!= null -> {
                            optimizationsDone++
                            LiteralValue.optimalNumeric(-subexpr.asIntegerValue, subexpr.position)
                        }
                        subexpr.floatvalue != null -> {
                            optimizationsDone++
                            LiteralValue(DataType.FLOAT, floatvalue = -subexpr.floatvalue, position = subexpr.position)
                        }
                        else -> throw ExpressionError("can only take negative of int or float", subexpr.position)
                    }
                    expr.operator == "~" -> when {
                        subexpr.asIntegerValue != null -> {
                            optimizationsDone++
                            LiteralValue.optimalNumeric(subexpr.asIntegerValue.inv(), subexpr.position)
                        }
                        else -> throw ExpressionError("can only take bitwise inversion of int", subexpr.position)
                    }
                    expr.operator == "not" -> when {
                        subexpr.asIntegerValue != null -> {
                            optimizationsDone++
                            LiteralValue.fromBoolean(subexpr.asIntegerValue == 0, subexpr.position)
                        }
                        subexpr.floatvalue != null -> {
                            optimizationsDone++
                            LiteralValue.fromBoolean(subexpr.floatvalue == 0.0, subexpr.position)
                        }
                        else -> throw ExpressionError("can not take logical not of $subexpr", subexpr.position)
                    }
                    else -> throw ExpressionError(expr.operator, subexpr.position)
                }
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
                    from.type==DataType.WORD || to.type==DataType.WORD -> {
                        // range on word value boundaries
                        val rangeValue = from.asIntegerValue!!.rangeTo(to.asIntegerValue!!)
                        if (rangeValue.last - rangeValue.first > 65535) {
                            throw ExpressionError("amount of values in range exceeds 65535", range.position)
                        }
                        return LiteralValue(DataType.ARRAY_W, arrayvalue = rangeValue.map {
                            val v = LiteralValue(DataType.WORD, wordvalue = it, position = range.position)
                            v.parent = range.parent
                            v
                        }.toMutableList(), position = from.position)
                    }
                    from.type==DataType.BYTE && to.type==DataType.BYTE -> {
                        // range on byte value  boundaries
                        val rangeValue = from.bytevalue!!.rangeTo(to.bytevalue!!)
                        if (rangeValue.last - rangeValue.first > 65535) {
                            throw ExpressionError("amount of values in range exceeds 65535", range.position)
                        }
                        return LiteralValue(DataType.ARRAY, arrayvalue = rangeValue.map {
                            val v = LiteralValue(DataType.BYTE, bytevalue = it.toShort(), position = range.position)
                            v.parent = range.parent
                            v
                        }.toMutableList(), position = from.position)
                    }
                    from.strvalue != null && to.strvalue != null -> {
                        // char range
                        val rangevalue = from.strvalue[0].rangeTo(to.strvalue[0])
                        if (rangevalue.last - rangevalue.first > 65535) {
                            throw ExpressionError("amount of characters in range exceeds 65535", range.position)
                        }
                        val newval = LiteralValue(DataType.STR, strvalue = rangevalue.toList().joinToString(""), position = range.position)
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
        return LiteralValue.fromBoolean(leq.bytevalue == 1.toShort(), left.position)
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
        return LiteralValue.fromBoolean(leftvalue == rightvalue, left.position)
    }

    private fun comparegreaterequal(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot compute $left >= $right"
        return when {
            left.asIntegerValue!=null -> when {
                right.asIntegerValue!=null -> LiteralValue.fromBoolean(left.asIntegerValue >= right.asIntegerValue, left.position)
                right.floatvalue!=null -> LiteralValue.fromBoolean(left.asIntegerValue >= right.floatvalue, left.position)
                else -> throw ExpressionError(error, left.position)
            }
            left.floatvalue!=null -> when {
                right.asIntegerValue!=null -> LiteralValue.fromBoolean(left.floatvalue >= right.asIntegerValue, left.position)
                right.floatvalue!=null -> LiteralValue.fromBoolean(left.floatvalue >= right.floatvalue, left.position)
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
    }

    private fun comparelessequal(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot compute $left >= $right"
        return when {
            left.asIntegerValue!=null -> when {
                right.asIntegerValue!=null -> LiteralValue.fromBoolean(left.asIntegerValue <= right.asIntegerValue, left.position)
                right.floatvalue!=null -> LiteralValue.fromBoolean(left.asIntegerValue <= right.floatvalue, left.position)
                else -> throw ExpressionError(error, left.position)
            }
            left.floatvalue!=null -> when {
                right.asIntegerValue!=null -> LiteralValue.fromBoolean(left.floatvalue <= right.asIntegerValue, left.position)
                right.floatvalue!=null -> LiteralValue.fromBoolean(left.floatvalue <= right.floatvalue, left.position)
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
    }

    private fun comparegreater(left: LiteralValue, right: LiteralValue): LiteralValue {
        val leq = comparelessequal(left, right)
        return LiteralValue.fromBoolean(leq.bytevalue == 1.toShort(), left.position)
    }

    private fun compareless(left: LiteralValue, right: LiteralValue): LiteralValue {
        val leq = comparegreaterequal(left, right)
        return LiteralValue.fromBoolean(leq.bytevalue == 1.toShort(), left.position)
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
        if(left.type==DataType.BYTE) {
            if(right.asIntegerValue!=null) {
                return LiteralValue(DataType.BYTE, bytevalue = (left.bytevalue!!.toInt() xor (right.asIntegerValue and 255)).toShort(), position = left.position)
            }
        } else if(left.type==DataType.WORD) {
            if(right.asIntegerValue!=null) {
                return LiteralValue(DataType.WORD, wordvalue = left.wordvalue!! xor right.asIntegerValue, position = left.position)
            }
        }
        throw ExpressionError("cannot calculate $left ^ $right", left.position)
    }

    private fun bitwiseor(left: LiteralValue, right: LiteralValue): LiteralValue {
        if(left.type==DataType.BYTE) {
            if(right.asIntegerValue!=null) {
                return LiteralValue(DataType.BYTE, bytevalue = (left.bytevalue!!.toInt() or (right.asIntegerValue and 255)).toShort(), position = left.position)
            }
        } else if(left.type==DataType.WORD) {
            if(right.asIntegerValue!=null) {
                return LiteralValue(DataType.WORD, wordvalue = left.wordvalue!! or right.asIntegerValue, position = left.position)
            }
        }
        throw ExpressionError("cannot calculate $left | $right", left.position)
    }

    private fun bitwiseand(left: LiteralValue, right: LiteralValue): LiteralValue {
        if(left.type==DataType.BYTE) {
            if(right.asIntegerValue!=null) {
                return LiteralValue(DataType.BYTE, bytevalue = (left.bytevalue!!.toInt() or (right.asIntegerValue and 255)).toShort(), position = left.position)
            }
        } else if(left.type==DataType.WORD) {
            if(right.asIntegerValue!=null) {
                return LiteralValue(DataType.WORD, wordvalue = left.wordvalue!! or right.asIntegerValue, position = left.position)
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
        val error = "cannot multiply $left and $right"
        return when {
            left.asIntegerValue!=null -> when {
                right.asIntegerValue!=null -> LiteralValue.optimalNumeric(left.asIntegerValue * right.asIntegerValue, left.position)
                right.floatvalue!=null -> LiteralValue(DataType.FLOAT, floatvalue = left.asIntegerValue * right.floatvalue, position = left.position)
                right.strvalue!=null -> {
                    if(right.strvalue.length * left.asIntegerValue > 65535) throw ExpressionError("string too large", left.position)
                    LiteralValue(DataType.STR, strvalue = right.strvalue.repeat(left.asIntegerValue), position = left.position)
                }
                else -> throw ExpressionError(error, left.position)
            }
            left.floatvalue!=null -> when {
                right.asIntegerValue!=null -> LiteralValue(DataType.FLOAT, floatvalue = left.floatvalue * right.asIntegerValue, position = left.position)
                right.floatvalue!=null -> LiteralValue(DataType.FLOAT, floatvalue = left.floatvalue * right.floatvalue, position = left.position)
                else -> throw ExpressionError(error, left.position)
            }
            left.strvalue!=null -> when {
                right.asIntegerValue!=null -> {
                    if(left.strvalue.length * right.asIntegerValue > 65535) throw ExpressionError("string too large", left.position)
                    LiteralValue(DataType.STR, strvalue = left.strvalue.repeat(right.asIntegerValue), position = left.position)
                }
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
    }

    private fun divide(left: LiteralValue, right: LiteralValue): LiteralValue {
        val error = "cannot divide $left by $right"
        return when {
            left.asIntegerValue!=null -> when {
                right.asIntegerValue!=null -> {
                    if(right.asIntegerValue==0) throw ExpressionError("attempt to divide by zero", left.position)
                    LiteralValue.optimalNumeric(left.asIntegerValue / right.asIntegerValue, left.position)
                }
                right.floatvalue!=null -> {
                    if(right.floatvalue==0.0) throw ExpressionError("attempt to divide by zero", left.position)
                    LiteralValue(DataType.FLOAT, floatvalue = left.asIntegerValue / right.floatvalue, position = left.position)
                }
                else -> throw ExpressionError(error, left.position)
            }
            left.floatvalue!=null -> when {
                right.asIntegerValue!=null -> {
                    if(right.asIntegerValue==0) throw ExpressionError("attempt to divide by zero", left.position)
                    LiteralValue(DataType.FLOAT, floatvalue = left.floatvalue / right.asIntegerValue, position = left.position)
                }
                right.floatvalue!=null -> {
                    if(right.floatvalue==0.0) throw ExpressionError("attempt to divide by zero", left.position)
                    LiteralValue(DataType.FLOAT, floatvalue = left.floatvalue / right.floatvalue, position = left.position)
                }
                else -> throw ExpressionError(error, left.position)
            }
            else -> throw ExpressionError(error, left.position)
        }
    }
}
