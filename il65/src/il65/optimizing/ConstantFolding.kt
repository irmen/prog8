package il65.optimizing

import il65.ast.*
import il65.compiler.Petscii


class ConstantFolding(private val globalNamespace: INameScope) : IAstProcessor {
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


