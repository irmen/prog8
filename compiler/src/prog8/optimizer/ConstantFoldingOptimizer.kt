package prog8.optimizer

import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.processing.AstWalker
import prog8.ast.processing.IAstModification
import prog8.ast.statements.*
import prog8.compiler.target.CompilationTarget


// First thing to do is replace all constant identifiers with their actual value,
// and the array var initializer values and sizes.
// This is needed because further constant optimizations depend on those.
internal class ConstantIdentifierReplacer(private val program: Program, private val errors: ErrorReporter) : AstWalker() {
    private val noModifications = emptyList<IAstModification>()

    override fun after(identifier: IdentifierReference, parent: Node): Iterable<IAstModification> {
        // replace identifiers that refer to const value, with the value itself
        // if it's a simple type and if it's not a left hand side variable
        if(identifier.parent is AssignTarget)
            return noModifications
        var forloop = identifier.parent as? ForLoop
        if(forloop==null)
            forloop = identifier.parent.parent as? ForLoop
        if(forloop!=null && identifier===forloop.loopVar)
            return noModifications

        val cval = identifier.constValue(program) ?: return noModifications
        return when (cval.type) {
            in NumericDatatypes -> listOf(IAstModification.ReplaceNode(identifier, NumericLiteralValue(cval.type, cval.number, identifier.position), identifier.parent))
            in PassByReferenceDatatypes -> throw FatalAstException("pass-by-reference type should not be considered a constant")
            else -> noModifications
        }
    }

    override fun before(decl: VarDecl, parent: Node): Iterable<IAstModification> {
        // the initializer value can't refer to the variable itself (recursive definition)
        // TODO: use call graph for this?
        if(decl.value?.referencesIdentifiers(decl.name) == true || decl.arraysize?.index?.referencesIdentifiers(decl.name) == true) {
            errors.err("recursive var declaration", decl.position)
            return noModifications
        }

        if(decl.type==VarDeclType.CONST || decl.type==VarDeclType.VAR) {
            if(decl.isArray){
                if(decl.arraysize==null) {
                    // for arrays that have no size specifier (or a non-constant one) attempt to deduce the size
                    val arrayval = decl.value as? ArrayLiteralValue
                    if(arrayval!=null) {
                        return listOf(IAstModification.SetExpression(
                                { decl.arraysize = ArrayIndex(it, decl.position) },
                                NumericLiteralValue.optimalInteger(arrayval.value.size, decl.position),
                                decl
                        ))
                    }
                }
                else if(decl.arraysize?.size()==null) {
                    val size = decl.arraysize!!.index.constValue(program)
                    if(size!=null) {
                        return listOf(IAstModification.SetExpression(
                                { decl.arraysize = ArrayIndex(it, decl.position) },
                                size, decl
                        ))
                    }
                }
            }

            when(decl.datatype) {
                DataType.FLOAT -> {
                    // vardecl: for scalar float vars, promote constant integer initialization values to floats
                    val litval = decl.value as? NumericLiteralValue
                    if (litval!=null && litval.type in IntegerDatatypes) {
                        val newValue = NumericLiteralValue(DataType.FLOAT, litval.number.toDouble(), litval.position)
                        return listOf(IAstModification.ReplaceNode(decl.value!!, newValue, decl))
                    }
                }
                DataType.ARRAY_UB, DataType.ARRAY_B, DataType.ARRAY_UW, DataType.ARRAY_W -> {
                    val numericLv = decl.value as? NumericLiteralValue
                    val rangeExpr = decl.value as? RangeExpr
                    if(rangeExpr!=null) {
                        // convert the initializer range expression to an actual array
                        val declArraySize = decl.arraysize?.size()
                        if(declArraySize!=null && declArraySize!=rangeExpr.size())
                            errors.err("range expression size doesn't match declared array size", decl.value?.position!!)
                        val constRange = rangeExpr.toConstantIntegerRange()
                        if(constRange!=null) {
                            val eltType = rangeExpr.inferType(program).typeOrElse(DataType.UBYTE)
                            val newValue = if(eltType in ByteDatatypes) {
                                ArrayLiteralValue(InferredTypes.InferredType.known(decl.datatype),
                                        constRange.map { NumericLiteralValue(eltType, it.toShort(), decl.value!!.position) }.toTypedArray(),
                                        position = decl.value!!.position)
                            } else {
                                ArrayLiteralValue(InferredTypes.InferredType.known(decl.datatype),
                                        constRange.map { NumericLiteralValue(eltType, it, decl.value!!.position) }.toTypedArray(),
                                        position = decl.value!!.position)
                            }
                            return listOf(IAstModification.ReplaceNode(decl.value!!, newValue, decl))
                        }
                    }
                    if(numericLv!=null && numericLv.type==DataType.FLOAT)
                        errors.err("arraysize requires only integers here", numericLv.position)
                    val size = decl.arraysize?.size() ?: return noModifications
                    if (rangeExpr==null && numericLv!=null) {
                        // arraysize initializer is empty or a single int, and we know the size; create the arraysize.
                        val fillvalue = numericLv.number.toInt()
                        when(decl.datatype){
                            DataType.ARRAY_UB -> {
                                if(fillvalue !in 0..255)
                                    errors.err("ubyte value overflow", numericLv.position)
                            }
                            DataType.ARRAY_B -> {
                                if(fillvalue !in -128..127)
                                    errors.err("byte value overflow", numericLv.position)
                            }
                            DataType.ARRAY_UW -> {
                                if(fillvalue !in 0..65535)
                                    errors.err("uword value overflow", numericLv.position)
                            }
                            DataType.ARRAY_W -> {
                                if(fillvalue !in -32768..32767)
                                    errors.err("word value overflow", numericLv.position)
                            }
                            else -> {}
                        }
                        // create the array itself, filled with the fillvalue.
                        val array = Array(size) {fillvalue}.map { NumericLiteralValue(ArrayElementTypes.getValue(decl.datatype), it, numericLv.position) as Expression}.toTypedArray()
                        val refValue = ArrayLiteralValue(InferredTypes.InferredType.known(decl.datatype), array, position = numericLv.position)
                        return listOf(IAstModification.ReplaceNode(decl.value!!, refValue, decl))
                    }
                }
                DataType.ARRAY_F  -> {
                    val size = decl.arraysize?.size() ?: return noModifications
                    val litval = decl.value as? NumericLiteralValue
                    val rangeExpr = decl.value as? RangeExpr
                    if(rangeExpr!=null) {
                        // convert the initializer range expression to an actual array of floats
                        val declArraySize = decl.arraysize?.size()
                        if(declArraySize!=null && declArraySize!=rangeExpr.size())
                            errors.err("range expression size doesn't match declared array size", decl.value?.position!!)
                        val constRange = rangeExpr.toConstantIntegerRange()
                        if(constRange!=null) {
                            val newValue = ArrayLiteralValue(InferredTypes.InferredType.known(DataType.ARRAY_F),
                                        constRange.map { NumericLiteralValue(DataType.FLOAT, it.toDouble(), decl.value!!.position) }.toTypedArray(),
                                        position = decl.value!!.position)
                            return listOf(IAstModification.ReplaceNode(decl.value!!, newValue, decl))
                        }
                    }
                    if(rangeExpr==null && litval!=null) {
                        // arraysize initializer is a single int, and we know the size.
                        val fillvalue = litval.number.toDouble()
                        if (fillvalue < CompilationTarget.machine.FLOAT_MAX_NEGATIVE || fillvalue > CompilationTarget.machine.FLOAT_MAX_POSITIVE)
                            errors.err("float value overflow", litval.position)
                        else {
                            // create the array itself, filled with the fillvalue.
                            val array = Array(size) {fillvalue}.map { NumericLiteralValue(DataType.FLOAT, it, litval.position) as Expression}.toTypedArray()
                            val refValue = ArrayLiteralValue(InferredTypes.InferredType.known(DataType.ARRAY_F), array, position = litval.position)
                            return listOf(IAstModification.ReplaceNode(decl.value!!, refValue, decl))
                        }
                    }
                }
                else -> {
                    // nothing to do for this type
                    // this includes strings and structs
                }
            }
        }

        val declValue = decl.value
        if(declValue!=null && decl.type==VarDeclType.VAR
                && declValue is NumericLiteralValue && !declValue.inferType(program).istype(decl.datatype)) {
            // cast the numeric literal to the appropriate datatype of the variable
            return listOf(IAstModification.ReplaceNode(decl.value!!, declValue.castNoCheck(decl.datatype), decl))
        }

        return noModifications
    }
}


internal class ConstantFoldingOptimizer(private val program: Program) : AstWalker() {
    private val noModifications = emptyList<IAstModification>()

    override fun before(memread: DirectMemoryRead, parent: Node): Iterable<IAstModification> {
        // @( &thing )  -->  thing
        val addrOf = memread.addressExpression as? AddressOf
        return if(addrOf!=null)
            listOf(IAstModification.ReplaceNode(memread, addrOf.identifier, parent))
        else
            noModifications
    }

    override fun after(expr: PrefixExpression, parent: Node): Iterable<IAstModification> {
        // Try to turn a unary prefix expression into a single constant value.
        // Compile-time constant sub expressions will be evaluated on the spot.
        // For instance, the expression for "- 4.5" will be optimized into the float literal -4.5
        val subexpr = expr.expression
        if (subexpr is NumericLiteralValue) {
            // accept prefixed literal values (such as -3, not true)
            return when (expr.operator) {
                "+" -> listOf(IAstModification.ReplaceNode(expr, subexpr, parent))
                "-" -> when (subexpr.type) {
                    in IntegerDatatypes -> {
                        listOf(IAstModification.ReplaceNode(expr,
                                NumericLiteralValue.optimalInteger(-subexpr.number.toInt(), subexpr.position),
                                parent))
                    }
                    DataType.FLOAT -> {
                        listOf(IAstModification.ReplaceNode(expr,
                                NumericLiteralValue(DataType.FLOAT, -subexpr.number.toDouble(), subexpr.position),
                                parent))
                    }
                    else -> throw ExpressionError("can only take negative of int or float", subexpr.position)
                }
                "~" -> when (subexpr.type) {
                    in IntegerDatatypes -> {
                        listOf(IAstModification.ReplaceNode(expr,
                                NumericLiteralValue.optimalInteger(subexpr.number.toInt().inv(), subexpr.position),
                                parent))
                    }
                    else -> throw ExpressionError("can only take bitwise inversion of int", subexpr.position)
                }
                "not" -> {
                    listOf(IAstModification.ReplaceNode(expr,
                            NumericLiteralValue.fromBoolean(subexpr.number.toDouble() == 0.0, subexpr.position),
                            parent))
                }
                else -> throw ExpressionError(expr.operator, subexpr.position)
            }
        }
        return noModifications
    }

    /**
     * Try to constfold a binary expression.
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
    override fun after(expr: BinaryExpression, parent: Node): Iterable<IAstModification> {
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
                val change = groupTwoConstsTogether(expr, subExpr,
                        leftconst != null, rightconst != null,
                        subleftconst != null, subrightconst != null)
                return change?.let { listOf(it) } ?: noModifications
            }
        }

        // const fold when both operands are a const
        if(leftconst != null && rightconst != null) {
            val evaluator = ConstExprEvaluator()
            val result = evaluator.evaluate(leftconst, expr.operator, rightconst)
            return listOf(IAstModification.ReplaceNode(expr, result, parent))
        }

        return noModifications
    }

    override fun after(array: ArrayLiteralValue, parent: Node): Iterable<IAstModification> {
        // because constant folding can result in arrays that are now suddenly capable
        // of telling the type of all their elements (for instance, when they contained -2 which
        // was a prefix expression earlier), we recalculate the array's datatype.
        if(array.type.isKnown)
            return noModifications

        // if the array literalvalue is inside an array vardecl, take the type from that
        // otherwise infer it from the elements of the array
        val vardeclType = (array.parent as? VarDecl)?.datatype
        if(vardeclType!=null) {
            val newArray = array.cast(vardeclType)
            if (newArray != null && newArray != array)
                return listOf(IAstModification.ReplaceNode(array, newArray, parent))
        } else {
            val arrayDt = array.guessDatatype(program)
            if (arrayDt.isKnown) {
                val newArray = array.cast(arrayDt.typeOrElse(DataType.STRUCT))
                if (newArray != null && newArray != array)
                    return listOf(IAstModification.ReplaceNode(array, newArray, parent))
            }
        }

        return noModifications
    }

    override fun after(functionCall: FunctionCall, parent: Node): Iterable<IAstModification> {
        // the args of a fuction are constfolded via recursion already.
        val constvalue = functionCall.constValue(program)
        return if(constvalue!=null)
            listOf(IAstModification.ReplaceNode(functionCall, constvalue, parent))
        else
            noModifications
    }

    override fun after(forLoop: ForLoop, parent: Node): Iterable<IAstModification> {
        fun adjustRangeDt(rangeFrom: NumericLiteralValue, targetDt: DataType, rangeTo: NumericLiteralValue, stepLiteral: NumericLiteralValue?, range: RangeExpr): RangeExpr {
            val newFrom: NumericLiteralValue
            val newTo: NumericLiteralValue
            try {
                newFrom = rangeFrom.castNoCheck(targetDt)
                newTo = rangeTo.castNoCheck(targetDt)
            } catch (x: ExpressionError) {
                return range
            }
            val newStep: Expression = try {
                stepLiteral?.castNoCheck(targetDt)?: range.step
            } catch(ee: ExpressionError) {
                range.step
            }
            return RangeExpr(newFrom, newTo, newStep, range.position)
        }

        // adjust the datatype of a range expression in for loops to the loop variable.
        val iterableRange = forLoop.iterable as? RangeExpr ?: return noModifications
        val rangeFrom = iterableRange.from as? NumericLiteralValue
        val rangeTo = iterableRange.to as? NumericLiteralValue
        if(rangeFrom==null || rangeTo==null) return noModifications

        val loopvar = forLoop.loopVar.targetVarDecl(program.namespace)!!
        val stepLiteral = iterableRange.step as? NumericLiteralValue
        when(loopvar.datatype) {
            DataType.UBYTE -> {
                if(rangeFrom.type!= DataType.UBYTE) {
                    // attempt to translate the iterable into ubyte values
                    val newIter = adjustRangeDt(rangeFrom, loopvar.datatype, rangeTo, stepLiteral, iterableRange)
                    return listOf(IAstModification.ReplaceNode(forLoop.iterable, newIter, forLoop))
                }
            }
            DataType.BYTE -> {
                if(rangeFrom.type!= DataType.BYTE) {
                    // attempt to translate the iterable into byte values
                    val newIter = adjustRangeDt(rangeFrom, loopvar.datatype, rangeTo, stepLiteral, iterableRange)
                    return listOf(IAstModification.ReplaceNode(forLoop.iterable, newIter, forLoop))
                }
            }
            DataType.UWORD -> {
                if(rangeFrom.type!= DataType.UWORD) {
                    // attempt to translate the iterable into uword values
                    val newIter = adjustRangeDt(rangeFrom, loopvar.datatype, rangeTo, stepLiteral, iterableRange)
                    return listOf(IAstModification.ReplaceNode(forLoop.iterable, newIter, forLoop))
                }
            }
            DataType.WORD -> {
                if(rangeFrom.type!= DataType.WORD) {
                    // attempt to translate the iterable into word values
                    val newIter = adjustRangeDt(rangeFrom, loopvar.datatype, rangeTo, stepLiteral, iterableRange)
                    return listOf(IAstModification.ReplaceNode(forLoop.iterable, newIter, forLoop))
                }
            }
            else -> throw FatalAstException("invalid loopvar datatype $loopvar")
        }

        return noModifications
    }

    override fun after(decl: VarDecl, parent: Node): Iterable<IAstModification> {
        val numval = decl.value as? NumericLiteralValue
        if(decl.type== VarDeclType.CONST && numval!=null) {
            val valueDt = numval.inferType(program)
            if(!valueDt.istype(decl.datatype)) {
                val adjustedVal = numval.castNoCheck(decl.datatype)
                return listOf(IAstModification.ReplaceNode(numval, adjustedVal, decl))
            }
        }
        return noModifications
    }

    private class ShuffleOperands(val expr: BinaryExpression,
                                  val exprOperator: String?,
                                  val subExpr: BinaryExpression,
                                  val newExprLeft: Expression?,
                                  val newExprRight: Expression?,
                                  val newSubexprLeft: Expression?,
                                  val newSubexprRight: Expression?
                                  ): IAstModification {
        override fun perform() {
            if(exprOperator!=null) expr.operator = exprOperator
            if(newExprLeft!=null) expr.left = newExprLeft
            if(newExprRight!=null) expr.right = newExprRight
            if(newSubexprLeft!=null) subExpr.left = newSubexprLeft
            if(newSubexprRight!=null) subExpr.right = newSubexprRight
        }
    }

    private fun groupTwoConstsTogether(expr: BinaryExpression,
                                       subExpr: BinaryExpression,
                                       leftIsConst: Boolean,
                                       rightIsConst: Boolean,
                                       subleftIsConst: Boolean,
                                       subrightIsConst: Boolean): IAstModification?
    {
        // todo: this implements only a small set of possible reorderings at this time
        if(expr.operator==subExpr.operator) {
            // both operators are the same.

            // If associative,  we can simply shuffle the const operands around to optimize.
            if(expr.operator in associativeOperators) {
                return if(leftIsConst) {
                    if(subleftIsConst)
                        ShuffleOperands(expr, null, subExpr, subExpr.right, null, null, expr.left)
                    else
                        ShuffleOperands(expr, null, subExpr, subExpr.left, null, expr.left, null)
                } else {
                    if(subleftIsConst)
                        ShuffleOperands(expr, null, subExpr, null, subExpr.right, null, expr.right)
                    else
                        ShuffleOperands(expr, null, subExpr, null, subExpr.left, expr.right, null)
                }
            }

            // If - or /,  we simetimes must reorder more, and flip operators (- -> +, / -> *)
            if(expr.operator=="-" || expr.operator=="/") {
                if(leftIsConst) {
                    return if (subleftIsConst) {
                        ShuffleOperands(expr, if (expr.operator == "-") "+" else "*", subExpr, subExpr.right, null, expr.left, subExpr.left)
                    } else {
                        IAstModification.ReplaceNode(expr,
                                BinaryExpression(
                                        BinaryExpression(expr.left, if (expr.operator == "-") "+" else "*", subExpr.right, subExpr.position),
                                        expr.operator, subExpr.left, expr.position),
                                expr.parent)
                    }
                } else {
                    return if(subleftIsConst) {
                        return ShuffleOperands(expr, null, subExpr, null, subExpr.right, null, expr.right)
                    } else {
                        IAstModification.ReplaceNode(expr,
                                BinaryExpression(
                                        subExpr.left, expr.operator,
                                        BinaryExpression(expr.right, if (expr.operator == "-") "+" else "*", subExpr.right, subExpr.position),
                                        expr.position),
                                expr.parent)
                    }
                }
            }
            return null

        }
        else
        {

            if(expr.operator=="/" && subExpr.operator=="*") {
                if(leftIsConst) {
                    val change = if(subleftIsConst) {
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
                    return IAstModification.ReplaceNode(expr, change, expr.parent)
                } else {
                    val change = if(subleftIsConst) {
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
                    return IAstModification.ReplaceNode(expr, change, expr.parent)
                }
            }
            else if(expr.operator=="*" && subExpr.operator=="/") {
                if(leftIsConst) {
                    val change = if(subleftIsConst) {
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
                    return IAstModification.ReplaceNode(expr, change, expr.parent)
                } else {
                    val change = if(subleftIsConst) {
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
                    return IAstModification.ReplaceNode(expr, change, expr.parent)
                }
            }
            else if(expr.operator=="+" && subExpr.operator=="-") {
                if(leftIsConst){
                    val change = if(subleftIsConst){
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
                    return IAstModification.ReplaceNode(expr, change, expr.parent)
                } else {
                    val change = if(subleftIsConst) {
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
                    return IAstModification.ReplaceNode(expr, change, expr.parent)
                }
            }
            else if(expr.operator=="-" && subExpr.operator=="+") {
                if(leftIsConst) {
                    val change = if(subleftIsConst) {
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
                    return IAstModification.ReplaceNode(expr, change, expr.parent)
                } else {
                    val change = if(subleftIsConst) {
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
                    return IAstModification.ReplaceNode(expr, change, expr.parent)
                }
            }

            return null
        }
    }

}
