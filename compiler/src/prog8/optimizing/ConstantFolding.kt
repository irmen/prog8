package prog8.optimizing

import prog8.ast.*
import prog8.compiler.HeapValues
import prog8.compiler.target.c64.Petscii


class ConstantFolding(private val namespace: INameScope, private val heap: HeapValues) : IAstProcessor {
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
            when(decl.datatype) {
                DataType.FLOAT -> {
                    // vardecl: for float vars, promote constant integer initialization values to floats
                    val literal = decl.value as? LiteralValue
                    if (literal != null && (literal.type == DataType.BYTE || literal.type==DataType.WORD)) {
                        val newValue = LiteralValue(DataType.FLOAT, floatvalue = literal.asNumericValue!!.toDouble(), position = literal.position)
                        decl.value = newValue
                    }
                }
                DataType.BYTE, DataType.WORD -> {
                    // vardecl: for byte/word vars, convert char/string of length 1 initialization values to integer
                    val literal = decl.value as? LiteralValue
                    if (literal != null && literal.isString && literal.strvalue?.length == 1) {
                        val petscii = Petscii.encodePetscii(literal.strvalue)[0]
                        val newValue = LiteralValue(DataType.BYTE, bytevalue = petscii, position = literal.position)
                        decl.value = newValue
                    }
                }
                DataType.MATRIX -> {
                    (decl.value as? LiteralValue)?.let {
                        val intvalue = it.asIntegerValue
                        if(intvalue!=null) {
                            // replace the single int value by a properly sized array to fill the matrix.
                            val size = decl.arrayspec!!.size()
                            if(size!=null) {
                                val newArray = Array<IExpression>(size) { _ -> LiteralValue(DataType.BYTE, bytevalue = intvalue.toShort(), position = it.position) }
                                decl.value = LiteralValue(DataType.ARRAY, arrayvalue = newArray, position = it.position)
                            } else {
                                addError(SyntaxError("matrix size spec must be constant integer values", it.position))
                            }
                        }
                    }
                }
                DataType.ARRAY, DataType.ARRAY_W -> {
                    (decl.value as? LiteralValue)?.let {
                        val intvalue = it.asIntegerValue
                        if(intvalue!=null) {
                            // replace the single int value by a properly sized array to fill the array with.
                            val size = decl.arrayspec!!.size()
                            if(size!=null) {
                                val newArray = Array<IExpression>(size) { _ ->
                                    if (decl.datatype == DataType.ARRAY)
                                        LiteralValue(DataType.BYTE, bytevalue = intvalue.toShort(), position = it.position)
                                    else
                                        LiteralValue(DataType.WORD, wordvalue = intvalue, position = it.position)
                                }
                                decl.value = LiteralValue(decl.datatype, arrayvalue = newArray, position=it.position)
                            } else {
                                addError(SyntaxError("array size must be a constant integer value", it.position))
                            }
                        }
                    }
                }
                else -> return result
            }
        }
        return result
    }

    /**
     * replace identifiers that refer to const value, with the value itself
     */
    override fun process(identifier: IdentifierReference): IExpression {
        return try {
            val cval = identifier.constValue(namespace, heap) ?: return identifier
            val copy = LiteralValue(cval.type, cval.bytevalue, cval.wordvalue, cval.floatvalue, cval.strvalue, cval.arrayvalue, position=identifier.position)
            copy.parent = identifier.parent
            return copy
        } catch (ax: AstException) {
            addError(ax)
            identifier
        }
    }

    override fun process(functionCall: FunctionCall): IExpression {
        return try {
            super.process(functionCall)
            functionCall.constValue(namespace, heap) ?: functionCall
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
     *
     * More complex stuff: reordering to group constants:
     * If one of our operands is a Constant,
     *   and the other operand is a Binary expression,
     *   and one of ITS operands is a Constant,
     *   and ITS other operand is NOT a Constant,
     *   ...it may be possible to rewrite the expression to group the two Constants together,
     *      to allow them to be const-folded away.
     */
    override fun process(expr: BinaryExpression): IExpression {
        return try {
            super.process(expr)
            val leftconst = expr.left.constValue(namespace, heap)
            val rightconst = expr.right.constValue(namespace, heap)

            val subExpr: BinaryExpression? = when {
                leftconst!=null -> expr.right as? BinaryExpression
                rightconst!=null -> expr.left as? BinaryExpression
                else -> null
            }
            if(subExpr!=null) {
                val subleftconst = subExpr.left.constValue(namespace, heap)
                val subrightconst = subExpr.right.constValue(namespace, heap)
                if ((subleftconst != null && subrightconst == null) || (subleftconst==null && subrightconst!=null))
                    // try reordering.
                    return groupTwoConstsTogether(expr, subExpr,
                            leftconst!=null, rightconst!=null,
                            subleftconst!=null, subrightconst!=null)
            }

            // const fold when both operands are a const
            val evaluator = ConstExprEvaluator()
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

    private fun groupTwoConstsTogether(expr: BinaryExpression,
                                       subExpr: BinaryExpression,
                                       leftIsConst: Boolean,
                                       rightIsConst: Boolean,
                                       subleftIsConst: Boolean,
                                       subrightIsConst: Boolean): IExpression
    {
        // @todo this implements only a small set of possible reorderings for now

        if(expr.operator==subExpr.operator) {
            // both operators are the same.
            // If + or *,  we can simply swap the const of expr and Var in subexpr.
            if(expr.operator=="+" || expr.operator=="*") {
                if(leftIsConst) {
                    if(subleftIsConst)
                        expr.left = subExpr.right.also { subExpr.right = expr.left }
                    else
                        expr.left = subExpr.left.also { subExpr.left = expr.left }
                } else {
                    if(subleftIsConst)
                        expr.right = subExpr.right.also {subExpr.right = expr.right }
                    else
                        expr.right = subExpr.left.also { subExpr.left = expr.right }
                }
                optimizationsDone++
                return expr
            }

            // If - or /,  we simetimes must reorder more, and flip operators (- -> +, / -> *)
            if(expr.operator=="-" || expr.operator=="/") {
                optimizationsDone++
                if(leftIsConst) {
                    if(subleftIsConst) {
                        val tmp = subExpr.right
                        subExpr.right = subExpr.left
                        subExpr.left = expr.left
                        expr.left = tmp
                        expr.operator = if(expr.operator=="-") "+" else "*"
                    } else
                        return BinaryExpression(
                                BinaryExpression(expr.left, if(expr.operator=="-") "+" else "*", subExpr.right, subExpr.position),
                                expr.operator, subExpr.left, expr.position)
                } else {
                    if(subleftIsConst)
                        expr.right = subExpr.right.also {subExpr.right = expr.right}
                    else
                        return BinaryExpression(
                                subExpr.left, expr.operator,
                                BinaryExpression(expr.right, if(expr.operator=="-") "+" else "*", subExpr.right, subExpr.position),
                                expr.position)
                }
                return expr
            }

            // todo: other cases where both operators are identical
            return expr
        } else {
            // todo: other combinations of operators and constants (with different operator in subexpr)
            return expr
        }
    }

    override fun process(range: RangeExpr): IExpression {
        range.from = range.from.process(this)
        range.to = range.to.process(this)
        range.step = range.step.process(this)
        return super.process(range)
    }

    override fun process(literalValue: LiteralValue): LiteralValue {
        if(literalValue.strvalue!=null) {
            // intern the string; move it into the heap
            if(literalValue.strvalue.length !in 1..255)
                addError(ExpressionError("string literal length must be between 1 and 255", literalValue.position))
            else {
                val heapId = heap.add(literalValue.type, literalValue.strvalue)     // TODO: we don't know the actual string type yet, STR != STR_P etc...
                val newValue = LiteralValue(literalValue.type, heapId = heapId, position = literalValue.position)
                return super.process(newValue)
            }
        } else if(literalValue.arrayvalue!=null) {
            val newArray = literalValue.arrayvalue.map { it.process(this) }.toTypedArray()
            // determine if the values are all bytes or that we need a word array instead
            var arrayDt = DataType.ARRAY
            var allElementsAreConstant = true
            for (expr in newArray) {
                allElementsAreConstant = allElementsAreConstant and (expr is LiteralValue)
                val valueDt = expr.resultingDatatype(namespace, heap)
                if(valueDt==DataType.BYTE)
                    continue
                else {
                    arrayDt = DataType.ARRAY_W
                    break
                }
            }

            // if the values are all constants, the array is moved to the heap
            if(allElementsAreConstant) {
                val array = newArray.map {
                    val litval = it as? LiteralValue
                    if(litval==null) {
                        addError(ExpressionError("array/matrix literal can contain only constant values", literalValue.position))
                        return super.process(literalValue)
                    }
                    if(litval.bytevalue==null && litval.wordvalue==null) {
                        if(arrayDt==DataType.ARRAY)
                            addError(ExpressionError("byte array elements must all be integers 0..255", literalValue.position))
                        else
                            addError(ExpressionError("word array elements must all be integers 0..65535", literalValue.position))
                        return super.process(literalValue)
                    }
                    val integer = litval.asIntegerValue!!
                    if(arrayDt==DataType.ARRAY && integer !in 0..255) {
                        addError(ExpressionError("byte array elements must all be integers 0..255", literalValue.position))
                        return super.process(literalValue)
                    } else if(arrayDt==DataType.ARRAY_W && integer !in 0..65535) {
                        addError(ExpressionError("word array elements must all be integers 0..65535", literalValue.position))
                        return super.process(literalValue)
                    }
                    integer
                }.toIntArray()
                val heapId = heap.add(arrayDt, array)
                val newValue = LiteralValue(arrayDt, heapId=heapId, position = literalValue.position)
                return super.process(newValue)
            } else {
                addError(ExpressionError("array/matrix literal can contain only constant values", literalValue.position))
            }

            val newValue = LiteralValue(arrayDt, arrayvalue = newArray, position = literalValue.position)
            return super.process(newValue)
        }
        return super.process(literalValue)
    }
}


