package prog8.optimizer

import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.ExpressionError
import prog8.ast.base.FatalAstException
import prog8.ast.base.UndefinedSymbolError
import prog8.ast.expressions.*
import prog8.ast.maySwapOperandOrder
import prog8.ast.statements.ForLoop
import prog8.ast.statements.VarDecl
import prog8.ast.statements.VarDeclType
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.code.core.AssociativeOperators
import prog8.code.core.DataType
import prog8.code.core.IntegerDatatypes


class ConstantFoldingOptimizer(private val program: Program) : AstWalker() {

    override fun before(memread: DirectMemoryRead, parent: Node): Iterable<IAstModification> {
        // @( &thing )  -->  thing
        val addrOf = memread.addressExpression as? AddressOf
        return if(addrOf!=null)
            listOf(IAstModification.ReplaceNode(memread, addrOf.identifier, parent))
        else
            noModifications
    }

    override fun after(containment: ContainmentCheck, parent: Node): Iterable<IAstModification> {
        val result = containment.constValue(program)
        if(result!=null)
            return listOf(IAstModification.ReplaceNode(containment, result, parent))
        return noModifications
    }

    override fun after(expr: PrefixExpression, parent: Node): Iterable<IAstModification> {
        // Try to turn a unary prefix expression into a single constant value.
        // Compile-time constant sub expressions will be evaluated on the spot.
        // For instance, the expression for "- 4.5" will be optimized into the float literal -4.5
        val subexpr = expr.expression
        if (subexpr is NumericLiteral) {
            // accept prefixed literal values (such as -3, not true)
            return when (expr.operator) {
                "+" -> listOf(IAstModification.ReplaceNode(expr, subexpr, parent))
                "-" -> when (subexpr.type) {
                    in IntegerDatatypes -> {
                        listOf(IAstModification.ReplaceNode(expr,
                                NumericLiteral.optimalInteger(-subexpr.number.toInt(), subexpr.position),
                                parent))
                    }
                    DataType.FLOAT -> {
                        listOf(IAstModification.ReplaceNode(expr,
                                NumericLiteral(DataType.FLOAT, -subexpr.number, subexpr.position),
                                parent))
                    }
                    else -> throw ExpressionError("can only take negative of int or float", subexpr.position)
                }
                "~" -> when (subexpr.type) {
                    DataType.BYTE -> {
                        listOf(IAstModification.ReplaceNode(expr,
                                NumericLiteral(DataType.BYTE, subexpr.number.toInt().inv().toDouble(), subexpr.position),
                                parent))
                    }
                    DataType.UBYTE -> {
                        listOf(IAstModification.ReplaceNode(expr,
                                NumericLiteral(DataType.UBYTE, (subexpr.number.toInt().inv() and 255).toDouble(), subexpr.position),
                                parent))
                    }
                    DataType.WORD -> {
                        listOf(IAstModification.ReplaceNode(expr,
                                NumericLiteral(DataType.WORD, subexpr.number.toInt().inv().toDouble(), subexpr.position),
                                parent))
                    }
                    DataType.UWORD -> {
                        listOf(IAstModification.ReplaceNode(expr,
                                NumericLiteral(DataType.UWORD, (subexpr.number.toInt().inv() and 65535).toDouble(), subexpr.position),
                                parent))
                    }
                    else -> throw ExpressionError("can only take bitwise inversion of int", subexpr.position)
                }
                "not" -> {
                    listOf(IAstModification.ReplaceNode(expr,
                            NumericLiteral.fromBoolean(subexpr.number == 0.0, subexpr.position),
                            parent))
                }
                else -> throw ExpressionError(expr.operator, subexpr.position)
            }
        }
        return noModifications
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
        val modifications = mutableListOf<IAstModification>()
        val leftconst = expr.left.constValue(program)
        val rightconst = expr.right.constValue(program)

        if(expr.left.inferType(program) istype DataType.STR) {
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
                val concatStr = StringLiteral(concatenated, leftString.encoding, expr.position)
                return listOf(IAstModification.ReplaceNode(expr, concatStr, parent))
            }
            else if(expr.operator=="*" && rightconst!=null && expr.left is StringLiteral) {
                // mutiply a string.
                val part = expr.left as StringLiteral
                val newStr = StringLiteral(part.value.repeat(rightconst.number.toInt()), part.encoding, expr.position)
                return listOf(IAstModification.ReplaceNode(expr, newStr, parent))
            }
        }

        if(expr.operator=="==" && rightconst!=null) {
            val leftExpr = expr.left as? BinaryExpression
            if(leftExpr!=null) {
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

        if(expr.inferType(program) istype DataType.FLOAT) {
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

        val evaluator = ConstExprEvaluator()

        // const fold when both operands are a const
        if(leftconst != null && rightconst != null) {
            val result = evaluator.evaluate(leftconst, expr.operator, rightconst)
            modifications += IAstModification.ReplaceNode(expr, result, parent)
        }

        if(leftconst==null && rightconst!=null && rightconst.number<0.0) {
            if (expr.operator == "-") {
                // X - -1 ---> X + 1
                val posNumber = NumericLiteral.optimalNumeric(-rightconst.number, rightconst.position)
                val plusExpr = BinaryExpression(expr.left, "+", posNumber, expr.position)
                return listOf(IAstModification.ReplaceNode(expr, plusExpr, parent))
            }
            else if (expr.operator == "+") {
                // X + -1 ---> X - 1
                val posNumber = NumericLiteral.optimalNumeric(-rightconst.number, rightconst.position)
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
                        //  (X / C2) * rightConst   -->  X * (rightConst/C2)
                        val constants = BinaryExpression(rightconst, "/", c2, c2.position)
                        val newExpr = BinaryExpression(leftBinExpr.left, "*", constants, expr.position)
                        return listOf(IAstModification.ReplaceNode(expr, newExpr, parent))
                    }
                }
            }
            else if(expr.operator=="/") {
                val c2 = leftBinExpr.right.constValue(program)
                if(c2!=null && leftBinExpr.operator=="/") {
                    // (X / C1) / C2   -->  X / (C1*C2)
                    // NOTE: do not optimize  (X * C1) / C2   -->  X * (C1/C2)  because this causes precision loss on integers
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
                        //(X * C) <plusmin> (Y * C)  =>  (X <plusmin> Y) * C
                        val xwithy = BinaryExpression(leftBinExpr.left, expr.operator, rightBinExpr.left, expr.position)
                        val newExpr = BinaryExpression(xwithy, "*", c1, expr.position)
                        modifications += IAstModification.ReplaceNode(expr, newExpr, parent)
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
                val newArray = array.cast(arrayDt.getOr(DataType.UNDEFINED))
                if (newArray != null && newArray != array)
                    return listOf(IAstModification.ReplaceNode(array, newArray, parent))
            }
        }

        return noModifications
    }

    override fun after(functionCallExpr: FunctionCallExpression, parent: Node): Iterable<IAstModification> {
        // the args of a fuction are constfolded via recursion already.
        val constvalue = functionCallExpr.constValue(program)
        return if(constvalue!=null)
            listOf(IAstModification.ReplaceNode(functionCallExpr, constvalue, parent))
        else
            noModifications
    }

    override fun after(forLoop: ForLoop, parent: Node): Iterable<IAstModification> {
        fun adjustRangeDt(rangeFrom: NumericLiteral, targetDt: DataType, rangeTo: NumericLiteral, stepLiteral: NumericLiteral?, range: RangeExpression): RangeExpression? {
            val fromCast = rangeFrom.cast(targetDt)
            val toCast = rangeTo.cast(targetDt)
            if(!fromCast.isValid || !toCast.isValid)
                return null

            val newStep =
                if(stepLiteral!=null) {
                    val stepCast = stepLiteral.cast(targetDt)
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

        val loopvar = forLoop.loopVar.targetVarDecl(program) ?: throw UndefinedSymbolError(forLoop.loopVar)

        val stepLiteral = iterableRange.step as? NumericLiteral
        when(loopvar.datatype) {
            DataType.UBYTE -> {
                if(rangeFrom.type!= DataType.UBYTE) {
                    // attempt to translate the iterable into ubyte values
                    val newIter = adjustRangeDt(rangeFrom, loopvar.datatype, rangeTo, stepLiteral, iterableRange)
                    if(newIter!=null)
                        return listOf(IAstModification.ReplaceNode(forLoop.iterable, newIter, forLoop))
                }
            }
            DataType.BYTE -> {
                if(rangeFrom.type!= DataType.BYTE) {
                    // attempt to translate the iterable into byte values
                    val newIter = adjustRangeDt(rangeFrom, loopvar.datatype, rangeTo, stepLiteral, iterableRange)
                    if(newIter!=null)
                        return listOf(IAstModification.ReplaceNode(forLoop.iterable, newIter, forLoop))
                }
            }
            DataType.UWORD -> {
                if(rangeFrom.type!= DataType.UWORD) {
                    // attempt to translate the iterable into uword values
                    val newIter = adjustRangeDt(rangeFrom, loopvar.datatype, rangeTo, stepLiteral, iterableRange)
                    if(newIter!=null)
                        return listOf(IAstModification.ReplaceNode(forLoop.iterable, newIter, forLoop))
                }
            }
            DataType.WORD -> {
                if(rangeFrom.type!= DataType.WORD) {
                    // attempt to translate the iterable into word values
                    val newIter = adjustRangeDt(rangeFrom, loopvar.datatype, rangeTo, stepLiteral, iterableRange)
                    if(newIter!=null)
                        return listOf(IAstModification.ReplaceNode(forLoop.iterable, newIter, forLoop))
                }
            }
            else -> throw FatalAstException("invalid loopvar datatype $loopvar")
        }

        return noModifications
    }

    override fun after(decl: VarDecl, parent: Node): Iterable<IAstModification> {
        val numval = decl.value as? NumericLiteral
        if(decl.type== VarDeclType.CONST && numval!=null) {
            val valueDt = numval.inferType(program)
            if(valueDt isnot decl.datatype) {
                val cast = numval.cast(decl.datatype)
                if(cast.isValid)
                    return listOf(IAstModification.ReplaceNode(numval, cast.valueOrZero(), decl))
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
        // NOTE: THIS IS ONLY VALID ON FLOATING POINT CONSTANTS

        // TODO: this implements only a small set of possible reorderings at this time, we could think of more
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
