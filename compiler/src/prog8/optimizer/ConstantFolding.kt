package prog8.optimizer

import prog8.ast.*
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.processing.IAstModifyingVisitor
import prog8.ast.statements.*
import prog8.compiler.HeapValues
import prog8.compiler.IntegerOrAddressOf
import prog8.compiler.target.c64.MachineDefinition.FLOAT_MAX_NEGATIVE
import prog8.compiler.target.c64.MachineDefinition.FLOAT_MAX_POSITIVE
import kotlin.math.floor


class ConstantFolding(private val program: Program) : IAstModifyingVisitor {
    var optimizationsDone: Int = 0
    var errors : MutableList<AstException> = mutableListOf()

    private val reportedErrorMessages = mutableSetOf<String>()

    fun addError(x: AstException) {
        // check that we don't add the isSameAs error more than once
        if(x.toString() !in reportedErrorMessages) {
            reportedErrorMessages.add(x.toString())
            errors.add(x)
        }
    }

    override fun visit(decl: VarDecl): IStatement {
        // the initializer value can't refer to the variable itself (recursive definition)
        // TODO: use call tree for this?
        if(decl.value?.referencesIdentifiers(decl.name) == true || decl.arraysize?.index?.referencesIdentifiers(decl.name) == true) {
            errors.add(ExpressionError("recursive var declaration", decl.position))
            return decl
        }

        if(decl.type==VarDeclType.CONST || decl.type==VarDeclType.VAR) {
            val refLv = decl.value as? ReferenceLiteralValue
            if(refLv!=null && refLv.isArray && refLv.heapId!=null)
                fixupArrayTypeOnHeap(decl, refLv)

            if(decl.isArray){
                // for arrays that have no size specifier (or a non-constant one) attempt to deduce the size
                if(decl.arraysize==null) {
                    val arrayval = (decl.value as? ReferenceLiteralValue)?.array
                    if(arrayval!=null) {
                        decl.arraysize = ArrayIndex(NumericLiteralValue.optimalInteger(arrayval.size, decl.position), decl.position)
                        optimizationsDone++
                    }
                }
                else if(decl.arraysize?.size()==null) {
                    val size = decl.arraysize!!.index.accept(this)
                    if(size is NumericLiteralValue) {
                        decl.arraysize = ArrayIndex(size, decl.position)
                        optimizationsDone++
                    }
                }
            }

            when(decl.datatype) {
                DataType.FLOAT -> {
                    // vardecl: for scalar float vars, promote constant integer initialization values to floats
                    val litval = decl.value as? NumericLiteralValue
                    if (litval!=null && litval.type in IntegerDatatypes) {
                        val newValue = NumericLiteralValue(DataType.FLOAT, litval.number.toDouble(), litval.position)
                        decl.value = newValue
                        optimizationsDone++
                        return decl
                    }
                }
                in StringDatatypes -> {
                    // nothing to do for strings
                }
                DataType.STRUCT -> {
                    // struct defintions don't have anything else in them
                }
                DataType.ARRAY_UB, DataType.ARRAY_B, DataType.ARRAY_UW, DataType.ARRAY_W -> {
                    val numericLv = decl.value as? NumericLiteralValue
                    val rangeExpr = decl.value as? RangeExpr
                    if(rangeExpr!=null) {
                        // convert the initializer range expression to an actual array (will be put on heap later)
                        val declArraySize = decl.arraysize?.size()
                        if(declArraySize!=null && declArraySize!=rangeExpr.size())
                            errors.add(ExpressionError("range expression size doesn't match declared array size", decl.value?.position!!))
                        val constRange = rangeExpr.toConstantIntegerRange()
                        if(constRange!=null) {
                            val eltType = rangeExpr.inferType(program)!!
                            if(eltType in ByteDatatypes) {
                                decl.value = ReferenceLiteralValue(decl.datatype,
                                        array = constRange.map { NumericLiteralValue(eltType, it.toShort(), decl.value!!.position) }
                                                .toTypedArray(), position = decl.value!!.position)
                            } else {
                                decl.value = ReferenceLiteralValue(decl.datatype,
                                        array = constRange.map { NumericLiteralValue(eltType, it, decl.value!!.position) }
                                                .toTypedArray(), position = decl.value!!.position)
                            }
                            decl.value!!.linkParents(decl)
                            optimizationsDone++
                            return decl
                        }
                    }
                    if(numericLv!=null && numericLv.type== DataType.FLOAT)
                        errors.add(ExpressionError("arraysize requires only integers here", numericLv.position))
                    val size = decl.arraysize?.size() ?: return decl
                    if (rangeExpr==null && numericLv!=null) {
                        // arraysize initializer is empty or a single int, and we know the size; create the arraysize.
                        val fillvalue = numericLv.number.toInt()
                        when(decl.datatype){
                            DataType.ARRAY_UB -> {
                                if(fillvalue !in 0..255)
                                    errors.add(ExpressionError("ubyte value overflow", numericLv.position))
                            }
                            DataType.ARRAY_B -> {
                                if(fillvalue !in -128..127)
                                    errors.add(ExpressionError("byte value overflow", numericLv.position))
                            }
                            DataType.ARRAY_UW -> {
                                if(fillvalue !in 0..65535)
                                    errors.add(ExpressionError("uword value overflow", numericLv.position))
                            }
                            DataType.ARRAY_W -> {
                                if(fillvalue !in -32768..32767)
                                    errors.add(ExpressionError("word value overflow", numericLv.position))
                            }
                            else -> {}
                        }
                        val heapId = program.heap.addIntegerArray(decl.datatype, Array(size) { IntegerOrAddressOf(fillvalue, null) })
                        decl.value = ReferenceLiteralValue(decl.datatype, initHeapId = heapId, position = numericLv.position)
                        optimizationsDone++
                        return decl
                    }
                }
                DataType.ARRAY_F  -> {
                    val litval = decl.value as? NumericLiteralValue
                    val size = decl.arraysize?.size() ?: return decl
                    // arraysize initializer is empty or a single int, and we know the size; create the arraysize.
                    val fillvalue = if (litval == null) 0.0 else litval.number.toDouble()
                    if(fillvalue< FLOAT_MAX_NEGATIVE || fillvalue> FLOAT_MAX_POSITIVE)
                        errors.add(ExpressionError("float value overflow", litval?.position ?: decl.position))
                    else {
                        val heapId = program.heap.addDoublesArray(DoubleArray(size) { fillvalue })
                        decl.value = ReferenceLiteralValue(DataType.ARRAY_F, initHeapId = heapId, position = litval?.position ?: decl.position)
                        optimizationsDone++
                        return decl
                    }
                }
                else -> {
                    // nothing to do for this type
                }
            }
        }

        return super.visit(decl)
    }

    private fun fixupArrayTypeOnHeap(decl: VarDecl, litval: ReferenceLiteralValue) {
        // fix the type of the array value that's on the heap, to match the vardecl.
        // notice that checking the bounds of the actual values is not done here, but in the AstChecker later.

        if(decl.datatype==litval.type)
            return   // already correct datatype
        val heapId = litval.heapId ?: throw FatalAstException("expected array to be on heap $litval")
        val array = program.heap.get(heapId)
        when(decl.datatype) {
            DataType.ARRAY_UB, DataType.ARRAY_B, DataType.ARRAY_UW, DataType.ARRAY_W -> {
                if(array.array!=null) {
                    program.heap.update(heapId, HeapValues.HeapValue(decl.datatype, null, array.array, null))
                    decl.value = ReferenceLiteralValue(decl.datatype, initHeapId = heapId, position = litval.position)
                }
            }
            DataType.ARRAY_F -> {
                if(array.array!=null) {
                    // convert a non-float array to floats
                    val doubleArray = array.array.map { it.integer!!.toDouble() }.toDoubleArray()
                    program.heap.update(heapId, HeapValues.HeapValue(DataType.ARRAY_F, null, null, doubleArray))
                    decl.value = ReferenceLiteralValue(decl.datatype, initHeapId = heapId, position = litval.position)
                }
            }
            DataType.STRUCT -> {
                // leave it alone for structs.
            }
            else -> throw FatalAstException("invalid array vardecl type ${decl.datatype}")
        }
    }

    /**
     * replace identifiers that refer to const value, with the value itself (if it's a simple type)
     */
    override fun visit(identifier: IdentifierReference): IExpression {
        return try {
            val cval = identifier.constValue(program) ?: return identifier
            return when {
                cval.type in NumericDatatypes -> {
                    val copy = NumericLiteralValue(cval.type, cval.number, identifier.position)
                    copy.parent = identifier.parent
                    copy
                }
                cval.type in PassByReferenceDatatypes -> TODO("ref type $identifier")
                else -> identifier
            }
        } catch (ax: AstException) {
            addError(ax)
            identifier
        }
    }

    override fun visit(functionCall: FunctionCall): IExpression {
        return try {
            super.visit(functionCall)
            typeCastConstArguments(functionCall)
            functionCall.constValue(program) ?: functionCall
        } catch (ax: AstException) {
            addError(ax)
            functionCall
        }
    }

    override fun visit(functionCallStatement: FunctionCallStatement): IStatement {
        super.visit(functionCallStatement)
        typeCastConstArguments(functionCallStatement)
        return functionCallStatement
    }

    private fun typeCastConstArguments(functionCall: IFunctionCall) {
        val subroutine = functionCall.target.targetSubroutine(program.namespace)
        if(subroutine!=null) {
            // if types differ, try to typecast constant arguments to the function call to the desired data type of the parameter
            for(arg in functionCall.arglist.withIndex().zip(subroutine.parameters)) {
                val expectedDt = arg.second.type
                val argConst = arg.first.value.constValue(program)
                if(argConst!=null && argConst.type!=expectedDt) {
                    val convertedValue = argConst.cast(expectedDt)
                    if(convertedValue!=null) {
                        functionCall.arglist[arg.first.index] = convertedValue
                        optimizationsDone++
                    }
                }
            }
        }
    }

    override fun visit(memread: DirectMemoryRead): IExpression {
        // @( &thing )  -->  thing
        val addrOf = memread.addressExpression as? AddressOf
        if(addrOf!=null)
            return super.visit(addrOf.identifier)
        return super.visit(memread)
    }

    /**
     * Try to accept a unary prefix expression.
     * Compile-time constant sub expressions will be evaluated on the spot.
     * For instance, the expression for "- 4.5" will be optimized into the float literal -4.5
     */
    override fun visit(expr: PrefixExpression): IExpression {
        return try {
            super.visit(expr)

            val subexpr = expr.expression
            if (subexpr is NumericLiteralValue) {
                // accept prefixed literal values (such as -3, not true)
                return when {
                    expr.operator == "+" -> subexpr
                    expr.operator == "-" -> when {
                        subexpr.type in IntegerDatatypes -> {
                            optimizationsDone++
                            NumericLiteralValue.optimalNumeric(-subexpr.number.toInt(), subexpr.position)
                        }
                        subexpr.type == DataType.FLOAT -> {
                            optimizationsDone++
                            NumericLiteralValue(DataType.FLOAT, -subexpr.number.toDouble(), subexpr.position)
                        }
                        else -> throw ExpressionError("can only take negative of int or float", subexpr.position)
                    }
                    expr.operator == "~" -> when {
                        subexpr.type in IntegerDatatypes -> {
                            optimizationsDone++
                            NumericLiteralValue.optimalNumeric(subexpr.number.toInt().inv(), subexpr.position)
                        }
                        else -> throw ExpressionError("can only take bitwise inversion of int", subexpr.position)
                    }
                    expr.operator == "not" -> {
                        optimizationsDone++
                        NumericLiteralValue.fromBoolean(subexpr.number.toDouble() == 0.0, subexpr.position)
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
     * Try to accept a binary expression.
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
    override fun visit(expr: BinaryExpression): IExpression {
        return try {
            super.visit(expr)
            val leftconst = expr.left.constValue(program)
            val rightconst = expr.right.constValue(program)

            val subExpr: BinaryExpression? = when {
                leftconst!=null -> expr.right as? BinaryExpression
                rightconst!=null -> expr.left as? BinaryExpression
                else -> null
            }
            if(subExpr!=null) {
                val subleftconst = subExpr.left.constValue(program)
                val subrightconst = subExpr.right.constValue(program)
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
            // both operators are the isSameAs.
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
                                BinaryExpression(expr.left, if (expr.operator == "-") "+" else "*", subExpr.right, subExpr.position),
                                expr.operator, subExpr.left, expr.position)
                } else {
                    return if(subleftIsConst) {
                        expr.right = subExpr.right.also { subExpr.right = expr.right }
                        expr
                    } else
                        BinaryExpression(
                                subExpr.left, expr.operator,
                                BinaryExpression(expr.right, if (expr.operator == "-") "+" else "*", subExpr.right, subExpr.position),
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
                        // (V/C1)*C2 -> (C1/C2)*V
                        BinaryExpression(
                                BinaryExpression(expr.right, "/", subExpr.right, subExpr.position),
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

    override fun visit(forLoop: ForLoop): IStatement {

        fun adjustRangeDt(rangeFrom: NumericLiteralValue, targetDt: DataType, rangeTo: NumericLiteralValue, stepLiteral: NumericLiteralValue?, range: RangeExpr): RangeExpr {
            val newFrom = rangeFrom.cast(targetDt)
            val newTo = rangeTo.cast(targetDt)
            if (newFrom != null && newTo != null) {
                val newStep: IExpression =
                        if (stepLiteral != null) (stepLiteral.cast(targetDt) ?: stepLiteral) else range.step
                return RangeExpr(newFrom, newTo, newStep, range.position)
            }
            return range
        }

        // adjust the datatype of a range expression in for loops to the loop variable.
        val resultStmt = super.visit(forLoop) as ForLoop
        val iterableRange = resultStmt.iterable as? RangeExpr ?: return resultStmt
        val rangeFrom = iterableRange.from as? NumericLiteralValue
        val rangeTo = iterableRange.to as? NumericLiteralValue
        if(rangeFrom==null || rangeTo==null) return resultStmt

        val loopvar = resultStmt.loopVar?.targetVarDecl(program.namespace)
        if(loopvar!=null) {
            val stepLiteral = iterableRange.step as? NumericLiteralValue
            when(loopvar.datatype) {
                DataType.UBYTE -> {
                    if(rangeFrom.type!= DataType.UBYTE) {
                        // attempt to translate the iterable into ubyte values
                        resultStmt.iterable = adjustRangeDt(rangeFrom, loopvar.datatype, rangeTo, stepLiteral, iterableRange)
                    }
                }
                DataType.BYTE -> {
                    if(rangeFrom.type!= DataType.BYTE) {
                        // attempt to translate the iterable into byte values
                        resultStmt.iterable = adjustRangeDt(rangeFrom, loopvar.datatype, rangeTo, stepLiteral, iterableRange)
                    }
                }
                DataType.UWORD -> {
                    if(rangeFrom.type!= DataType.UWORD) {
                        // attempt to translate the iterable into uword values
                        resultStmt.iterable = adjustRangeDt(rangeFrom, loopvar.datatype, rangeTo, stepLiteral, iterableRange)
                    }
                }
                DataType.WORD -> {
                    if(rangeFrom.type!= DataType.WORD) {
                        // attempt to translate the iterable into word values
                        resultStmt.iterable = adjustRangeDt(rangeFrom, loopvar.datatype, rangeTo, stepLiteral, iterableRange)
                    }
                }
                else -> throw FatalAstException("invalid loopvar datatype $loopvar")
            }
        }
        return resultStmt
    }

    override fun visit(refLiteral: ReferenceLiteralValue): IExpression {
        val litval = super.visit(refLiteral)
        if(litval is ReferenceLiteralValue) {
            if (litval.isString) {
                // intern the string; move it into the heap
                if (litval.str!!.length !in 1..255)
                    addError(ExpressionError("string literal length must be between 1 and 255", litval.position))
                else {
                    litval.addToHeap(program.heap)  // TODO: we don't know the actual string type yet, STR != STR_S etc...
                }
            } else if (litval.isArray) {
                // first, adjust the array datatype
                val litval2 = adjustArrayValDatatype(litval)
                litval2.addToHeap(program.heap)
                return litval2
            }
        }
        return litval
    }

    private fun adjustArrayValDatatype(litval: ReferenceLiteralValue): ReferenceLiteralValue {
        if(litval.array==null) {
            if(litval.heapId!=null)
                return litval       // thing is already on the heap, assume it's the right type
            throw FatalAstException("missing array value")
        }

        val typesInArray = litval.array.mapNotNull { it.inferType(program) }.toSet()
        val arrayDt =
                when {
                    litval.array.any { it is AddressOf } -> DataType.ARRAY_UW
                    DataType.FLOAT in typesInArray -> DataType.ARRAY_F
                    DataType.WORD in typesInArray -> DataType.ARRAY_W
                    else -> {
                        val allElementsAreConstantOrAddressOf = litval.array.fold(true) { c, expr-> c and (expr is NumericLiteralValue|| expr is AddressOf)}
                        if(!allElementsAreConstantOrAddressOf) {
                            addError(ExpressionError("array literal can only consist of constant primitive numerical values or memory pointers", litval.position))
                            return litval
                        } else {
                            val integerArray = litval.array.map { it.constValue(program)!!.number.toInt() }
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
                    }
                }

        if(arrayDt!=litval.type) {
            return ReferenceLiteralValue(arrayDt, array = litval.array, position = litval.position)
        }
        return litval
    }

    override fun visit(assignment: Assignment): IStatement {
        super.visit(assignment)
        val lv = assignment.value as? NumericLiteralValue
        if(lv!=null) {
            // see if we can promote/convert a literal value to the required datatype
            when(assignment.target.inferType(program, assignment)) {
                DataType.UWORD -> {
                    // we can convert to UWORD: any UBYTE, BYTE/WORD that are >=0, FLOAT that's an integer 0..65535,
                    if(lv.type== DataType.UBYTE)
                        assignment.value = NumericLiteralValue(DataType.UWORD, lv.number.toInt(), lv.position)
                    else if(lv.type== DataType.BYTE && lv.number.toInt()>=0)
                        assignment.value = NumericLiteralValue(DataType.UWORD, lv.number.toInt(), lv.position)
                    else if(lv.type== DataType.WORD && lv.number.toInt()>=0)
                        assignment.value = NumericLiteralValue(DataType.UWORD, lv.number.toInt(), lv.position)
                    else if(lv.type== DataType.FLOAT) {
                        val d = lv.number.toDouble()
                        if(floor(d)==d && d>=0 && d<=65535)
                            assignment.value = NumericLiteralValue(DataType.UWORD, floor(d).toInt(), lv.position)
                    }
                }
                DataType.UBYTE -> {
                    // we can convert to UBYTE: UWORD <=255, BYTE >=0, FLOAT that's an integer 0..255,
                    if(lv.type== DataType.UWORD && lv.number.toInt() <= 255)
                        assignment.value = NumericLiteralValue(DataType.UBYTE, lv.number.toShort(), lv.position)
                    else if(lv.type== DataType.BYTE && lv.number.toInt() >=0)
                        assignment.value = NumericLiteralValue(DataType.UBYTE, lv.number.toShort(), lv.position)
                    else if(lv.type== DataType.FLOAT) {
                        val d = lv.number.toDouble()
                        if(floor(d)==d && d >=0 && d<=255)
                            assignment.value = NumericLiteralValue(DataType.UBYTE, floor(d).toShort(), lv.position)
                    }
                }
                DataType.BYTE -> {
                    // we can convert to BYTE: UWORD/UBYTE <= 127, FLOAT that's an integer 0..127
                    if(lv.type== DataType.UWORD && lv.number.toInt() <= 127)
                        assignment.value = NumericLiteralValue(DataType.BYTE, lv.number.toShort(), lv.position)
                    else if(lv.type== DataType.UBYTE && lv.number.toInt() <= 127)
                        assignment.value = NumericLiteralValue(DataType.BYTE, lv.number.toShort(), lv.position)
                    else if(lv.type== DataType.FLOAT) {
                        val d = lv.number.toDouble()
                        if(floor(d)==d && d>=0 && d<=127)
                            assignment.value = NumericLiteralValue(DataType.BYTE, floor(d).toShort(), lv.position)
                    }
                }
                DataType.WORD -> {
                    // we can convert to WORD: any UBYTE/BYTE, UWORD <= 32767, FLOAT that's an integer -32768..32767,
                    if(lv.type== DataType.UBYTE || lv.type== DataType.BYTE)
                        assignment.value = NumericLiteralValue(DataType.WORD, lv.number.toInt(), lv.position)
                    else if(lv.type== DataType.UWORD && lv.number.toInt() <= 32767)
                        assignment.value = NumericLiteralValue(DataType.WORD, lv.number.toInt(), lv.position)
                    else if(lv.type== DataType.FLOAT) {
                        val d = lv.number.toDouble()
                        if(floor(d)==d && d>=-32768 && d<=32767)
                            assignment.value = NumericLiteralValue(DataType.BYTE, floor(d).toShort(), lv.position)
                    }
                }
                DataType.FLOAT -> {
                    assignment.value = NumericLiteralValue(DataType.FLOAT, lv.number.toDouble(), lv.position)
                }
                else -> {}
            }
        }
        return assignment
    }
}
