package prog8.optimizing

import prog8.ast.*
import prog8.compiler.CompilerException
import prog8.compiler.HeapValues
import prog8.compiler.target.c64.FLOAT_MAX_NEGATIVE
import prog8.compiler.target.c64.FLOAT_MAX_POSITIVE
import prog8.compiler.target.c64.Petscii
import kotlin.math.floor


class ConstantFolding(private val namespace: INameScope, private val heap: HeapValues) : IAstProcessor {
    var optimizationsDone: Int = 0
    var errors : MutableList<AstException> = mutableListOf()

    private val reportedErrorMessages = mutableSetOf<String>()

    fun addError(x: AstException) {
        // check that we don't add the same error more than once
        if(x.toString() !in reportedErrorMessages) {
            reportedErrorMessages.add(x.toString())
            errors.add(x)
        }
    }

    override fun process(decl: VarDecl): IStatement {
        // the initializer value can't refer to the variable itself (recursive definition)
        if(decl.value?.referencesIdentifier(decl.name) == true || decl.arrayspec?.x?.referencesIdentifier(decl.name) == true) {
            errors.add(ExpressionError("recursive var declaration", decl.position))
            return decl
        }

        val result = super.process(decl)

        if(decl.type==VarDeclType.CONST || decl.type==VarDeclType.VAR) {
            when(decl.datatype) {
                DataType.FLOAT -> {
                    // vardecl: for float vars, promote constant integer initialization values to floats
                    val literal = decl.value as? LiteralValue
                    if (literal != null && literal.type in IntegerDatatypes) {
                        val newValue = LiteralValue(DataType.FLOAT, floatvalue = literal.asNumericValue!!.toDouble(), position = literal.position)
                        decl.value = newValue
                    }
                }
                in IntegerDatatypes -> {
                    // vardecl: for byte/word vars, convert char/string of length 1 initialization values to ubyte integer
                    val literal = decl.value as? LiteralValue
                    if (literal != null && literal.isString && literal.strvalue(heap).length == 1) {
                        val petscii = Petscii.encodePetscii(literal.strvalue(heap), true)[0]
                        val newValue = LiteralValue(DataType.UBYTE, bytevalue = petscii, position = literal.position)
                        decl.value = newValue
                    }
                }
                DataType.ARRAY_UB, DataType.ARRAY_B, DataType.ARRAY_UW, DataType.ARRAY_W -> {
                    val litval = decl.value as? LiteralValue
                    if(litval?.type==DataType.FLOAT)
                        errors.add(ExpressionError("arrayspec requires only integers here", litval.position))
                    val size = decl.arrayspec!!.size()
                    if(litval!=null && litval.isArray) {
                        // arrayspec initializer value is an arrayspec already, keep as-is (or convert to WORDs if needed)
                        if(litval.heapId!=null) {
                            if(decl.datatype==DataType.ARRAY_UW && litval.type == DataType.ARRAY_UB) {
                                val array = heap.get(litval.heapId)
                                if(array.array!=null) {
                                    heap.update(litval.heapId, HeapValues.HeapValue(DataType.ARRAY_UW, null, array.array, null))
                                    decl.value = LiteralValue(decl.datatype, heapId = litval.heapId, position = litval.position)
                                }
                            } else if(decl.datatype==DataType.ARRAY_W && litval.type == DataType.ARRAY_B) {
                                val array = heap.get(litval.heapId)
                                if(array.array!=null) {
                                    heap.update(litval.heapId, HeapValues.HeapValue(DataType.ARRAY_W, null, array.array, null))
                                    decl.value = LiteralValue(decl.datatype, heapId = litval.heapId, position = litval.position)
                                }
                            }
                        }
                    } else if (size != null) {
                        // arrayspec initializer is empty or a single int, and we know the size; create the arrayspec.
                        val fillvalue = if (litval == null) 0 else litval.asIntegerValue ?: 0
                        when(decl.datatype){
                            DataType.ARRAY_UB -> {
                                if(fillvalue !in 0..255)
                                    errors.add(ExpressionError("ubyte value overflow", litval?.position ?: decl.position))
                            }
                            DataType.ARRAY_B -> {
                                if(fillvalue !in -128..127)
                                    errors.add(ExpressionError("byte value overflow", litval?.position ?: decl.position))
                            }
                            DataType.ARRAY_UW -> {
                                if(fillvalue !in 0..65535)
                                    errors.add(ExpressionError("uword value overflow", litval?.position ?: decl.position))
                            }
                            DataType.ARRAY_W -> {
                                if(fillvalue !in -32768..32767)
                                    errors.add(ExpressionError("word value overflow", litval?.position ?: decl.position))
                            }
                            else -> {}
                        }
                        val fillArray = IntArray(size) { fillvalue }
                        val heapId = heap.add(decl.datatype, fillArray)
                        decl.value = LiteralValue(decl.datatype, heapId = heapId, position = litval?.position ?: decl.position)
                    }
                }
                DataType.ARRAY_F  -> {
                    val litval = decl.value as? LiteralValue
                    val size = decl.arrayspec!!.size()
                    if(litval!=null && litval.isArray) {
                        // arrayspec initializer value is an arrayspec already, make sure to convert to floats
                        if(litval.heapId!=null) {
                            val array = heap.get(litval.heapId)
                            if (array.doubleArray == null) {
                                val doubleArray = array.array!!.map { it.toDouble() }.toDoubleArray()
                                heap.update(litval.heapId, HeapValues.HeapValue(DataType.ARRAY_F, null, null, doubleArray))
                                decl.value = LiteralValue(decl.datatype, heapId = litval.heapId, position = litval.position)
                            }
                        }
                    } else  if (size != null) {
                        // arrayspec initializer is empty or a single int, and we know the size; create the arrayspec.
                        val fillvalue = if (litval == null) 0.0 else litval.asNumericValue?.toDouble() ?: 0.0
                        if(fillvalue< FLOAT_MAX_NEGATIVE || fillvalue> FLOAT_MAX_POSITIVE)
                            errors.add(ExpressionError("float value overflow", litval?.position ?: decl.position))
                        else {
                            val fillArray = DoubleArray(size) { fillvalue }
                            val heapId = heap.add(decl.datatype, fillArray)
                            decl.value = LiteralValue(decl.datatype, heapId = heapId, position = litval?.position ?: decl.position)
                        }
                    }
                }
                else -> return result
            }
        }
        return result
    }

    /**
     * replace identifiers that refer to const value, with the value itself (if it's a simple type)
     */
    override fun process(identifier: IdentifierReference): IExpression {
        return try {
            val cval = identifier.constValue(namespace, heap) ?: return identifier
            return if(cval.isNumeric) {
                val copy = LiteralValue(cval.type, cval.bytevalue, cval.wordvalue, cval.floatvalue, null, cval.arrayvalue, position = identifier.position)
                copy.parent = identifier.parent
                copy
            } else
                identifier
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
     *
     *  examples include:
     *        (X / c1) * c2  ->  X / (c2/c1)
     *        (X + c1) - c2  ->  X + (c1-c2)
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
                if ((subleftconst != null && subrightconst == null) || (subleftconst==null && subrightconst!=null)) {
                    // try reordering.
                    return groupTwoConstsTogether(expr, subExpr,
                            leftconst != null, rightconst != null,
                            subleftconst != null, subrightconst != null)
                }
            }

            // const fold when both operands are a const
            val evaluator = ConstExprEvaluator()
            return when {
                leftconst != null && rightconst != null -> {
                    optimizationsDone++
                    evaluator.evaluate(leftconst, expr.operator, rightconst, heap)
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
                    return if(subleftIsConst) {
                        val tmp = subExpr.right
                        subExpr.right = subExpr.left
                        subExpr.left = expr.left
                        expr.left = tmp
                        expr.operator = if(expr.operator=="-") "+" else "*"
                        expr
                    } else
                        BinaryExpression(
                                BinaryExpression(expr.left, if(expr.operator=="-") "+" else "*", subExpr.right, subExpr.position),
                                expr.operator, subExpr.left, expr.position)
                } else {
                    return if(subleftIsConst) {
                        expr.right = subExpr.right.also { subExpr.right = expr.right }
                        expr
                    } else
                        BinaryExpression(
                                subExpr.left, expr.operator,
                                BinaryExpression(expr.right, if(expr.operator=="-") "+" else "*", subExpr.right, subExpr.position),
                                expr.position)
                }
            }
            return expr

        }
        else
        {

            if(expr.operator=="/" && subExpr.operator=="*") {
                optimizationsDone++
                if(leftIsConst) {
                    return if(subleftIsConst) {
                        // C1/(C2*V) -> (C1/C2)/V
                        BinaryExpression(
                                BinaryExpression(expr.left, "/", subExpr.left, subExpr.position),
                                "/",
                                subExpr.right, expr.position)
                    } else {
                        // C1/(V*C2) -> (C1/C2)/V
                        BinaryExpression(
                                BinaryExpression(expr.left, "/", subExpr.right, subExpr.position),
                                "/",
                                subExpr.left, expr.position)
                    }
                } else {
                    return if(subleftIsConst) {
                        // (C1*V)/C2 -> (C1/C2)*V
                        BinaryExpression(
                                BinaryExpression(subExpr.left, "/", expr.right, subExpr.position),
                                "*",
                                subExpr.right, expr.position)
                    } else {
                        // (V*C1)/C2 -> (C1/C2)*V
                        BinaryExpression(
                                BinaryExpression(subExpr.right, "/", expr.right, subExpr.position),
                                "*",
                                subExpr.left, expr.position)
                    }
                }
            }
            else if(expr.operator=="*" && subExpr.operator=="/") {
                optimizationsDone++
                if(leftIsConst) {
                    return if(subleftIsConst) {
                        // C1*(C2/V) -> (C1*C2)/V
                        BinaryExpression(
                                BinaryExpression(expr.left, "*", subExpr.left, subExpr.position),
                                "/",
                                subExpr.right, expr.position)
                    } else {
                        // C1*(V/C2) -> (C1/C2)*V
                        BinaryExpression(
                                BinaryExpression(expr.left, "/", subExpr.right, subExpr.position),
                                "*",
                                subExpr.left, expr.position)
                    }
                } else {
                    return if(subleftIsConst) {
                        // (C1/V)*C2 -> (C1*C2)/V
                        BinaryExpression(
                                BinaryExpression(subExpr.left, "*", expr.right, subExpr.position),
                                "/",
                                subExpr.right, expr.position)
                    } else {
                        // (V/C1)*C2 -> (C2/C1)*V
                        BinaryExpression(
                                BinaryExpression(subExpr.right, "/", expr.right, subExpr.position),
                                "*",
                                subExpr.left, expr.position)
                    }
                }
            }
            else if(expr.operator=="+" && subExpr.operator=="-") {
                optimizationsDone++
                if(leftIsConst){
                    return if(subleftIsConst){
                        // c1+(c2-v)  ->  (c1+c2)-v
                        BinaryExpression(
                                BinaryExpression(expr.left, "+", subExpr.left, subExpr.position),
                                "-",
                                subExpr.right, expr.position)
                    } else {
                        // c1+(v-c2)  ->  v+(c1-c2)
                        BinaryExpression(
                                BinaryExpression(expr.left, "-", subExpr.right, subExpr.position),
                                "+",
                                subExpr.left, expr.position)
                    }
                } else {
                    return if(subleftIsConst) {
                        // (c1-v)+c2  ->  (c1+c2)-v
                        BinaryExpression(
                                BinaryExpression(subExpr.left, "+", expr.right, subExpr.position),
                                "-",
                                subExpr.right, expr.position)
                    } else {
                        // (v-c1)+c2  ->  v+(c2-c1)
                        BinaryExpression(
                                BinaryExpression(expr.right, "-", subExpr.right, subExpr.position),
                                "+",
                                subExpr.left, expr.position)
                    }
                }
            }
            else if(expr.operator=="-" && subExpr.operator=="+") {
                optimizationsDone++
                if(leftIsConst) {
                    return if(subleftIsConst) {
                        // c1-(c2+v)  ->  (c1-c2)-v
                        BinaryExpression(
                                BinaryExpression(expr.left, "-", subExpr.left, subExpr.position),
                                "-",
                                subExpr.right, expr.position)
                    } else {
                        // c1-(v+c2)  ->  (c1-c2)-v
                        BinaryExpression(
                                BinaryExpression(expr.left, "-", subExpr.right, subExpr.position),
                                "-",
                                subExpr.left, expr.position)
                    }
                } else {
                    return if(subleftIsConst) {
                        // (c1+v)-c2  ->  v+(c1-c2)
                        BinaryExpression(
                                BinaryExpression(subExpr.left, "-", expr.right, subExpr.position),
                                "+",
                                subExpr.right, expr.position)
                    } else {
                        // (v+c1)-c2  ->  v+(c1-c2)
                        BinaryExpression(
                                BinaryExpression(subExpr.right, "-", expr.right, subExpr.position),
                                "+",
                                subExpr.left, expr.position)
                    }
                }
            }

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
        if(literalValue.isString) {
            // intern the string; move it into the heap
            if(literalValue.strvalue(heap).length !in 1..255)
                addError(ExpressionError("string literal length must be between 1 and 255", literalValue.position))
            else {
                val heapId = heap.add(literalValue.type, literalValue.strvalue(heap))     // TODO: we don't know the actual string type yet, STR != STR_P etc...
                val newValue = LiteralValue(literalValue.type, heapId = heapId, position = literalValue.position)
                return super.process(newValue)
            }
        } else if(literalValue.arrayvalue!=null) {
            return moveArrayToHeap(literalValue)
        }

        return super.process(literalValue)
    }

    private fun moveArrayToHeap(arraylit: LiteralValue): LiteralValue {
        val array: Array<IExpression> = arraylit.arrayvalue!!.map { it.process(this) }.toTypedArray()
        val allElementsAreConstant = array.fold(true) { c, expr-> c and (expr is LiteralValue)}
        if(!allElementsAreConstant) {
            addError(ExpressionError("array literal can contain only constant values", arraylit.position))
            return arraylit
        } else {
            val valuesInArray = array.map { it.constValue(namespace, heap)!!.asNumericValue!! }
            val integerArray = valuesInArray.map{it.toInt()}.toIntArray()
            val doubleArray = valuesInArray.map{it.toDouble()}.toDoubleArray()
            val typesInArray: Set<DataType> = array.mapNotNull { it.resultingDatatype(namespace, heap) }.toSet()
            val arrayDt =
                    if(DataType.FLOAT in typesInArray)
                        DataType.ARRAY_F
                    else if(DataType.WORD in typesInArray) {
                        if(DataType.UWORD in typesInArray)
                            DataType.ARRAY_F
                        else
                            DataType.ARRAY_W
                    } else {
                        val maxValue = integerArray.max()!!
                        val minValue = integerArray.min()!!
                        if (minValue >= 0) {
                            // unsigned
                            if (maxValue <= 255)
                                DataType.ARRAY_UB
                            else
                                DataType.ARRAY_UW
                        } else {
                            // signed
                            if (maxValue <= 127)
                                DataType.ARRAY_B
                            else
                                DataType.ARRAY_W
                        }
                    }

            val heapId = when(arrayDt) {
                DataType.ARRAY_UB,
                DataType.ARRAY_B,
                DataType.ARRAY_UW,
                DataType.ARRAY_W -> heap.add(arrayDt, integerArray)
                DataType.ARRAY_F -> heap.add(arrayDt, doubleArray)
                else -> throw CompilerException("invalid arrayspec type")
            }
            return LiteralValue(arrayDt, heapId = heapId, position = arraylit.position)
        }
    }

    override fun process(assignment: Assignment): IStatement {
        super.process(assignment)
        val lv = assignment.value as? LiteralValue
        if(lv!=null) {
            val targetDt = assignment.singleTarget?.determineDatatype(namespace, heap, assignment)
            // see if we can promote/convert a literal value to the required datatype
            when(targetDt) {
                DataType.UWORD -> {
                    // we can convert to UWORD: any UBYTE, BYTE/WORD that are >=0, FLOAT that's an integer 0..65535,
                    if(lv.type==DataType.UBYTE)
                        assignment.value = LiteralValue(DataType.UWORD, wordvalue = lv.asIntegerValue, position=lv.position)
                    else if(lv.type==DataType.BYTE && lv.bytevalue!!>=0)
                        assignment.value = LiteralValue(DataType.UWORD, wordvalue = lv.asIntegerValue, position=lv.position)
                    else if(lv.type==DataType.WORD && lv.bytevalue!!>=0)
                        assignment.value = LiteralValue(DataType.UWORD, wordvalue = lv.asIntegerValue, position=lv.position)
                    else if(lv.type==DataType.FLOAT) {
                        val d = lv.floatvalue!!
                        if(floor(d)==d && d>=0 && d<=65535)
                            assignment.value = LiteralValue(DataType.UWORD, wordvalue=floor(d).toInt(), position=lv.position)
                    }
                }
                DataType.UBYTE -> {
                    // we can convert to UBYTE: UWORD <=255, BYTE >=0, FLOAT that's an integer 0..255,
                    if(lv.type==DataType.UWORD && lv.wordvalue!! <= 255)
                        assignment.value = LiteralValue(DataType.UBYTE, lv.wordvalue.toShort(), position=lv.position)
                    else if(lv.type==DataType.BYTE && lv.bytevalue!! >=0)
                        assignment.value = LiteralValue(DataType.UBYTE, lv.bytevalue.toShort(), position=lv.position)
                    else if(lv.type==DataType.FLOAT) {
                        val d = lv.floatvalue!!
                        if(floor(d)==d && d >=0 && d<=255)
                            assignment.value = LiteralValue(DataType.UBYTE, floor(d).toShort(), position=lv.position)
                    }
                }
                DataType.BYTE -> {
                    // we can convert to BYTE: UWORD/UBYTE <= 127, FLOAT that's an integer 0..127
                    if(lv.type==DataType.UWORD && lv.wordvalue!! <= 127)
                        assignment.value = LiteralValue(DataType.BYTE, lv.wordvalue.toShort(), position=lv.position)
                    else if(lv.type==DataType.UBYTE && lv.bytevalue!! <= 127)
                        assignment.value = LiteralValue(DataType.BYTE, lv.bytevalue, position=lv.position)
                    else if(lv.type==DataType.FLOAT) {
                        val d = lv.floatvalue!!
                        if(floor(d)==d && d>=0 && d<=127)
                            assignment.value = LiteralValue(DataType.BYTE, floor(d).toShort(), position=lv.position)
                    }
                }
                DataType.WORD -> {
                    // we can convert to WORD: any UBYTE/BYTE, UWORD <= 32767, FLOAT that's an integer -32768..32767,
                    if(lv.type==DataType.UBYTE || lv.type==DataType.BYTE)
                        assignment.value = LiteralValue(DataType.WORD, wordvalue=lv.bytevalue!!.toInt(), position=lv.position)
                    else if(lv.type==DataType.UWORD && lv.wordvalue!! <= 32767)
                        assignment.value = LiteralValue(DataType.WORD, wordvalue=lv.wordvalue, position=lv.position)
                    else if(lv.type==DataType.FLOAT) {
                        val d = lv.floatvalue!!
                        if(floor(d)==d && d>=-32768 && d<=32767)
                            assignment.value = LiteralValue(DataType.BYTE, floor(d).toShort(), position=lv.position)
                    }
                }
                DataType.FLOAT -> {
                    if(lv.isNumeric)
                        assignment.value = LiteralValue(DataType.FLOAT, floatvalue= lv.asNumericValue?.toDouble(), position=lv.position)
                }
                else -> {}
            }
        }
        return assignment
    }
}


