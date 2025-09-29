package prog8.optimizer

import prog8.ast.FatalAstException
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.expressions.*
import prog8.ast.maySwapOperandOrder
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.code.core.AssociativeOperators
import prog8.code.core.BaseDataType
import prog8.code.core.IErrorReporter
import kotlin.math.floor


class ConstantFoldingOptimizer(private val program: Program, private val errors: IErrorReporter) : AstWalker() {

    private val evaluator = ConstExprEvaluator()

    override fun after(addressOf: AddressOf, parent: Node): Iterable<IAstModification> {
        val constAddr = addressOf.constValue(program) ?: return noModifications
        return listOf(IAstModification.ReplaceNode(addressOf, constAddr, parent))
    }

    override fun before(memread: DirectMemoryRead, parent: Node): Iterable<IAstModification> {
        // @( &thing )  -->  thing  (but only if thing is a byte type!)
        val addrOf = memread.addressExpression as? AddressOf
        if(addrOf!=null) {
            if(addrOf.identifier?.inferType(program)?.isBytes==true)
                return listOf(IAstModification.ReplaceNode(memread, addrOf.identifier!!, parent))
        }
        return noModifications
    }

    override fun after(numLiteral: NumericLiteral, parent: Node): Iterable<IAstModification> {

        if(numLiteral.type==BaseDataType.LONG) {
            // see if LONG values may be reduced to something smaller
            val smaller = NumericLiteral.optimalInteger(numLiteral.number.toInt(), numLiteral.position)
            if(smaller.type!=BaseDataType.LONG) {
                return listOf(IAstModification.ReplaceNode(numLiteral, smaller, parent))
            }
        }

        if(parent is Assignment) {
            val iDt = parent.target.inferType(program)
            if(iDt.isKnown && !iDt.isBool && !(iDt issimpletype numLiteral.type)) {
                val casted = numLiteral.cast(iDt.getOrUndef().base, true)
                if(casted.isValid) {
                    return listOf(IAstModification.ReplaceNode(numLiteral, casted.valueOrZero(), parent))
                }
            }
        }
        return noModifications
    }

    override fun after(containment: ContainmentCheck, parent: Node): Iterable<IAstModification> {
        val result = containment.constValue(program)
        if(result!=null)
            return listOf(IAstModification.ReplaceNode(containment, result, parent))
        return noModifications
    }

    override fun after(expr: PrefixExpression, parent: Node): Iterable<IAstModification> {
        val constValue = expr.constValue(program) ?: return noModifications
        return listOf(IAstModification.ReplaceNode(expr, constValue, parent))
    }

    /*
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
        if(expr.operator==".")
            return noModifications
        val modifications = mutableListOf<IAstModification>()
        val leftconst = expr.left.constValue(program)
        val rightconst = expr.right.constValue(program)

        if(expr.left.inferType(program).isStringLy) {
            if(expr.operator=="+" && expr.left is StringLiteral && expr.right is StringLiteral) {
                // concatenate two strings.
                val leftString = expr.left as StringLiteral
                val rightString = expr.right as StringLiteral
                val concatenated = if(leftString.encoding==rightString.encoding) {
                    leftString.value + rightString.value
                } else {
                    program.encoding.decodeString(
                        program.encoding.encodeString(leftString.value, leftString.encoding) + program.encoding.encodeString(rightString.value, rightString.encoding),
                        leftString.encoding)
                }
                val concatStr = StringLiteral.create(concatenated, leftString.encoding, expr.position)
                return listOf(IAstModification.ReplaceNode(expr, concatStr, parent))
            }
            else if(expr.operator=="*" && rightconst!=null && expr.left is StringLiteral) {
                // mutiply a string.
                val part = expr.left as StringLiteral
                if(part.value.isEmpty())
                    errors.warn("resulting string has length zero", part.position)
                val newStr = StringLiteral.create(part.value.repeat(rightconst.number.toInt()), part.encoding, expr.position)
                return listOf(IAstModification.ReplaceNode(expr, newStr, parent))
            }
        }

        if(expr.left.inferType(program).isArray) {
            if (expr.operator=="*" && rightconst!=null) {
                if (expr.left is ArrayLiteral) {
                    // concatenate array literal.
                    val part = expr.left as ArrayLiteral
                    if(part.value.isEmpty())
                        errors.warn("resulting array has length zero", part.position)
                    val tmp = mutableListOf<Expression>()
                    repeat(rightconst.number.toInt()) {
                        part.value.forEach { tmp += it.copy() }
                    }
                    val newArray = ArrayLiteral(part.type, tmp.toTypedArray(), part.position)
                    return listOf(IAstModification.ReplaceNode(expr, newArray, parent))
                }
                else {
                    val leftTarget = (expr.left as? IdentifierReference)?.targetVarDecl()
                    if(leftTarget!=null && leftTarget.origin==VarDeclOrigin.ARRAYLITERAL)
                        throw FatalAstException("shouldn't see an array literal converted to an autovar here")
                }
            }
        }

        if(expr.operator=="==" && rightconst!=null) {
            val leftExpr = expr.left as? BinaryExpression
            // only do this shuffling when the LHS is not a constant itself (otherwise problematic nested replacements)
            if(leftExpr?.constValue(program) != null) {
                val leftRightConst = leftExpr.right.constValue(program)
                if(leftRightConst!=null) {
                    when (leftExpr.operator) {
                        "+" -> {
                            // X + С1 == C2  -->  X == C2 - C1
                            val newRightConst = NumericLiteral(rightconst.type, rightconst.number - leftRightConst.number, rightconst.position)
                            return listOf(
                                IAstModification.ReplaceNode(leftExpr, leftExpr.left, expr),
                                IAstModification.ReplaceNode(expr.right, newRightConst, expr)
                            )
                        }
                        "-" -> {
                            // X - С1 == C2  -->  X == C2 + C1
                            val newRightConst = NumericLiteral(rightconst.type, rightconst.number + leftRightConst.number, rightconst.position)
                            return listOf(
                                IAstModification.ReplaceNode(leftExpr, leftExpr.left, expr),
                                IAstModification.ReplaceNode(expr.right, newRightConst, expr)
                            )
                        }
                    }
                }
            }
        }

        if(expr.inferType(program) issimpletype BaseDataType.FLOAT) {
            val subExpr: BinaryExpression? = when {
                leftconst != null -> expr.right as? BinaryExpression
                rightconst != null -> expr.left as? BinaryExpression
                else -> null
            }
            if (subExpr != null) {
                val subleftconst = subExpr.left.constValue(program)
                val subrightconst = subExpr.right.constValue(program)
                if ((subleftconst != null && subrightconst == null) || (subleftconst == null && subrightconst != null)) {
                    // try reordering.
                    val change = groupTwoFloatConstsTogether(
                        expr, subExpr,
                        leftconst != null, rightconst != null,
                        subleftconst != null, subrightconst != null
                    )
                    if (change != null)
                        modifications += change
                }
            }
        }

        // const fold when both operands are a const.
        // if in a chained comparison, that one has to be desugared first though.
        if(leftconst != null && rightconst != null) {
            val result = evaluator.evaluate(leftconst, expr.operator, rightconst)
            modifications += IAstModification.ReplaceNode(expr, result, parent)
        }

        if(leftconst==null && rightconst!=null && rightconst.number<0.0) {
            if (expr.operator == "-") {
                // X - -1 ---> X + 1
                val posNumber = NumericLiteral.optimalNumeric(rightconst.type, null, -rightconst.number, rightconst.position)
                val plusExpr = BinaryExpression(expr.left, "+", posNumber, expr.position)
                return listOf(IAstModification.ReplaceNode(expr, plusExpr, parent))
            }
            else if (expr.operator == "+") {
                // X + -1 ---> X - 1
                val posNumber = NumericLiteral.optimalNumeric(rightconst.type, null, -rightconst.number, rightconst.position)
                val plusExpr = BinaryExpression(expr.left, "-", posNumber, expr.position)
                return listOf(IAstModification.ReplaceNode(expr, plusExpr, parent))
            }
        }


        val leftBinExpr = expr.left as? BinaryExpression
        val rightBinExpr = expr.right as? BinaryExpression

        if(leftBinExpr!=null && rightconst!=null) {
            if(expr.operator=="+" || expr.operator=="-") {
                if(leftBinExpr.operator in listOf("+", "-")) {
                    val c2 = leftBinExpr.right.constValue(program)
                    if(c2!=null) {
                        // (X + C2) +/- rightConst  -->  X + (C2 +/- rightConst)
                        // (X - C2) +/- rightConst  -->  X - (C2 +/- rightConst)   mind the flipped right operator
                        val operator = if(leftBinExpr.operator=="+") expr.operator else if(expr.operator=="-") "+" else "-"
                        val constants = BinaryExpression(c2, operator, rightconst, c2.position)
                        val newExpr = BinaryExpression(leftBinExpr.left, leftBinExpr.operator, constants, expr.position)
                        return listOf(IAstModification.ReplaceNode(expr, newExpr, parent))
                    }
                }
            }
            else if(expr.operator=="*") {
                val c2 = leftBinExpr.right.constValue(program)
                if(c2!=null) {
                    if(leftBinExpr.operator=="*") {
                        // (X * C2) * rightConst  -->  X * (rightConst*C2)
                        val constants = BinaryExpression(rightconst, "*", c2, c2.position)
                        val newExpr = BinaryExpression(leftBinExpr.left, "*", constants, expr.position)
                        return listOf(IAstModification.ReplaceNode(expr, newExpr, parent))
                    } else if (leftBinExpr.operator=="/") {
                        if(expr.inferType(program) issimpletype BaseDataType.FLOAT) {
                            //  (X / C2) * rightConst   -->  X * (rightConst/C2)    only valid for floating point
                            val constants = BinaryExpression(rightconst, "/", c2, c2.position)
                            val newExpr = BinaryExpression(leftBinExpr.left, "*", constants, expr.position)
                            return listOf(IAstModification.ReplaceNode(expr, newExpr, parent))
                        }
                    }
                }
            }
            else if(expr.operator=="/") {
                val c2 = leftBinExpr.right.constValue(program)
                if(c2!=null && leftBinExpr.operator=="/") {
                    // (X / C1) / C2   -->  X / (C1*C2)
                    // NOTE: do not optimize  (X * C1) / C2  for integers,  -->  X * (C1/C2)  because this causes precision loss on integers
                    val constants = BinaryExpression(c2, "*", rightconst, c2.position)
                    val newExpr = BinaryExpression(leftBinExpr.left, "/", constants, expr.position)
                    return listOf(IAstModification.ReplaceNode(expr, newExpr, parent))
                }
            }
        }

        if(expr.operator=="+" || expr.operator=="-") {
            if(leftBinExpr!=null && rightBinExpr!=null) {
                val c1 = leftBinExpr.right.constValue(program)
                val c2 = rightBinExpr.right.constValue(program)
                if(leftBinExpr.operator=="+" && rightBinExpr.operator=="+") {
                    if (c1 != null && c2 != null) {
                        // (X + C1) <plusmin> (Y + C2)  =>  (X <plusmin> Y) + (C1 <plusmin> C2)
                        val c3 = evaluator.evaluate(c1, expr.operator, c2)
                        val xwithy = BinaryExpression(leftBinExpr.left, expr.operator, rightBinExpr.left, expr.position)
                        val newExpr = BinaryExpression(xwithy, "+", c3, expr.position)
                        modifications += IAstModification.ReplaceNode(expr, newExpr, parent)
                    }
                }
                else if(leftBinExpr.operator=="-" && rightBinExpr.operator=="-") {
                    if (c1 != null && c2 != null) {
                        // (X - C1) <plusmin> (Y - C2)  =>  (X <plusmin> Y) - (C1 <plusmin> C2)
                        val c3 = evaluator.evaluate(c1, expr.operator, c2)
                        val xwithy = BinaryExpression(leftBinExpr.left, expr.operator, rightBinExpr.left, expr.position)
                        val newExpr = BinaryExpression(xwithy, "-", c3, expr.position)
                        modifications += IAstModification.ReplaceNode(expr, newExpr, parent)
                    }
                }
                else if(leftBinExpr.operator=="*" && rightBinExpr.operator=="*"){
                    if (c1 != null && c2 != null && c1==c2) {
                        //(X * C) <plusmin> (Y * C)  =>  (X <plusmin> Y) * C    (only if types of X and Y are the same!)
                        val xDt = leftBinExpr.left.inferType(program)
                        val yDt = rightBinExpr.left.inferType(program)
                        if(xDt==yDt) {
                            val xwithy = BinaryExpression(leftBinExpr.left, expr.operator, rightBinExpr.left, expr.position)
                            val newExpr = BinaryExpression(xwithy, "*", c1, expr.position)
                            modifications += IAstModification.ReplaceNode(expr, newExpr, parent)
                        }
                    }
                }
            }
        }

        return modifications
    }

    override fun after(array: ArrayLiteral, parent: Node): Iterable<IAstModification> {
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
                val newArray = array.cast(arrayDt.getOrUndef())
                if (newArray != null && newArray != array)
                    return listOf(IAstModification.ReplaceNode(array, newArray, parent))
            }
        }

        return noModifications
    }

    override fun after(arrayIndexedExpression: ArrayIndexedExpression, parent: Node): Iterable<IAstModification> {
        if(parent is VarDecl && parent.parent is Block) {
            // only block level (global) initializers are considered here, because they're run just once at program startup

            val const = arrayIndexedExpression.constValue(program)
            if (const != null)
                return listOf(IAstModification.ReplaceNode(arrayIndexedExpression, const, parent))

            val constIndex = arrayIndexedExpression.indexer.constIndex()
            if (constIndex != null) {
                if(arrayIndexedExpression.plainarrayvar!=null) {
                    val arrayVar = arrayIndexedExpression.plainarrayvar!!.targetVarDecl()
                    if(arrayVar!=null) {
                        val array =arrayVar.value as? ArrayLiteral
                        if(array!=null) {
                            val value = array.value[constIndex].constValue(program)
                            if(value!=null) {
                                return listOf(IAstModification.ReplaceNode(arrayIndexedExpression, value, parent))
                            }
                        }
                    }
                } else if(arrayIndexedExpression.pointerderef!=null) {
                    TODO("constant fold pointer[i]")
                }
            }
        }
        return noModifications
    }

    override fun after(functionCallExpr: FunctionCallExpression, parent: Node): Iterable<IAstModification> {
        val constvalue = functionCallExpr.constValue(program)
        return if(constvalue!=null)
            listOf(IAstModification.ReplaceNode(functionCallExpr, constvalue, parent))
        else {
            val const2 = evaluator.evaluate(functionCallExpr, program)
            return if(const2!=null)
                listOf(IAstModification.ReplaceNode(functionCallExpr, const2, parent))
            else
                noModifications
        }
    }

    override fun after(forLoop: ForLoop, parent: Node): Iterable<IAstModification> {
        fun adjustRangeDt(rangeFrom: NumericLiteral, targetDt: BaseDataType, rangeTo: NumericLiteral, stepLiteral: NumericLiteral?, range: RangeExpression): RangeExpression? {
            val fromCast = rangeFrom.cast(targetDt, true)
            val toCast = rangeTo.cast(targetDt, true)
            if(!fromCast.isValid || !toCast.isValid)
                return null

            val newStep =
                if(stepLiteral!=null) {
                    val stepCast = stepLiteral.cast(targetDt, true)
                    if(stepCast.isValid)
                        stepCast.valueOrZero()
                    else
                        range.step
                } else {
                    range.step
                }

            return RangeExpression(fromCast.valueOrZero(), toCast.valueOrZero(), newStep, range.position)
        }

        // adjust the datatype of a range expression in for loops to the loop variable.
        val iterableRange = forLoop.iterable as? RangeExpression ?: return noModifications
        val rangeFrom = iterableRange.from as? NumericLiteral
        val rangeTo = iterableRange.to as? NumericLiteral
        if(rangeFrom==null || rangeTo==null) return noModifications

        val loopvar = forLoop.loopVar.targetVarDecl() ?: return noModifications

        val stepLiteral = iterableRange.step as? NumericLiteral
        require(loopvar.datatype.isBasic)
        when(val loopvarSimpleDt = loopvar.datatype.base) {
            BaseDataType.UBYTE -> {
                if(rangeFrom.type != BaseDataType.UBYTE) {
                    // attempt to translate the iterable into ubyte values
                    val newIter = adjustRangeDt(rangeFrom, loopvarSimpleDt, rangeTo, stepLiteral, iterableRange)
                    if(newIter!=null)
                        return listOf(IAstModification.ReplaceNode(forLoop.iterable, newIter, forLoop))
                }
            }
            BaseDataType.BYTE -> {
                if(rangeFrom.type != BaseDataType.BYTE) {
                    // attempt to translate the iterable into byte values
                    val newIter = adjustRangeDt(rangeFrom, loopvarSimpleDt, rangeTo, stepLiteral, iterableRange)
                    if(newIter!=null)
                        return listOf(IAstModification.ReplaceNode(forLoop.iterable, newIter, forLoop))
                }
            }
            BaseDataType.UWORD -> {
                if(rangeFrom.type != BaseDataType.UWORD) {
                    // attempt to translate the iterable into uword values
                    val newIter = adjustRangeDt(rangeFrom, loopvarSimpleDt, rangeTo, stepLiteral, iterableRange)
                    if(newIter!=null)
                        return listOf(IAstModification.ReplaceNode(forLoop.iterable, newIter, forLoop))
                }
            }
            BaseDataType.WORD -> {
                if(rangeFrom.type != BaseDataType.WORD) {
                    // attempt to translate the iterable into word values
                    val newIter = adjustRangeDt(rangeFrom, loopvarSimpleDt, rangeTo, stepLiteral, iterableRange)
                    if(newIter!=null)
                        return listOf(IAstModification.ReplaceNode(forLoop.iterable, newIter, forLoop))
                }
            }
            else -> { /* nothing for floats, these are not allowed in for loops and will give an error elsewhere */ }
        }

        return noModifications
    }

    override fun after(decl: VarDecl, parent: Node): Iterable<IAstModification> {
        val numval = decl.value as? NumericLiteral
        if(decl.type== VarDeclType.CONST && numval!=null) {
            val valueDt = numval.inferType(program)
            if(valueDt issimpletype BaseDataType.LONG || decl.datatype.isLong) {
                return noModifications  // this is handled in the numericalvalue case
            }
            if(!(valueDt istype decl.datatype)) {
                val cast = numval.cast(decl.datatype.base, true)
                if (cast.isValid) {
                    return listOf(IAstModification.ReplaceNode(numval, cast.valueOrZero(), decl))
                }
            }
        }
        return noModifications
    }

    override fun after(repeatLoop: RepeatLoop, parent: Node): Iterable<IAstModification> {
        val count = (repeatLoop.iterations as? NumericLiteral)?.number
        if(count!=null && floor(count)!=count) {
            val integer = NumericLiteral.optimalInteger(count.toInt(), repeatLoop.position)
            repeatLoop.iterations = integer
            integer.linkParents(repeatLoop)
        }
        return noModifications
    }

    override fun after(typecast: TypecastExpression, parent: Node): Iterable<IAstModification> {
        val constValue = typecast.constValue(program) ?: return noModifications
        return listOf(IAstModification.ReplaceNode(typecast, constValue, parent))
    }

    override fun after(subroutine: Subroutine, parent: Node): Iterable<IAstModification> {
        val address = subroutine.asmAddress?.address
        if(address!=null) {
            val constAddress = address.constValue(program)
            if(constAddress!=null) {
                subroutine.asmAddress!!.address = constAddress
                constAddress.parent = subroutine
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

    private fun groupTwoFloatConstsTogether(expr: BinaryExpression,
       subExpr: BinaryExpression,
       leftIsConst: Boolean,
       rightIsConst: Boolean,
       subleftIsConst: Boolean,
       subrightIsConst: Boolean): IAstModification?
    {
        // NOTE: THESE REORDERINGS ARE ONLY VALID FOR FLOATING POINT CONSTANTS

        if(expr.operator==subExpr.operator) {
            // both operators are the same.

            // If associative,  we can simply shuffle the const operands around to optimize.
            if(expr.operator in AssociativeOperators && maySwapOperandOrder(expr)) {
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
            else if(expr.operator=="*" && subExpr.operator=="/" && subExpr.inferType(program) issimpletype BaseDataType.FLOAT) {
                // division optimizations only valid for floats
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
