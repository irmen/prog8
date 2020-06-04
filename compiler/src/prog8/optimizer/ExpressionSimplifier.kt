package prog8.optimizer

import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.processing.AstWalker
import prog8.ast.processing.IAstModification
import prog8.ast.statements.Assignment
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow

/*
    todo add more expression optimizations

    Investigate what optimizations binaryen has, also see  https://egorbo.com/peephole-optimizations.html

 */


internal class ExpressionSimplifier(private val program: Program) : AstWalker() {
    private val powersOfTwo = (1..16).map { (2.0).pow(it) }.toSet()
    private val negativePowersOfTwo = powersOfTwo.map { -it }.toSet()

    override fun after(assignment: Assignment, parent: Node): Iterable<IAstModification> {
        if (assignment.aug_op != null)
            throw FatalAstException("augmented assignments should have been converted to normal assignments before this optimizer: $assignment")
        return emptyList()
    }

    override fun after(typecast: TypecastExpression, parent: Node): Iterable<IAstModification> {
        val mods = mutableListOf<IAstModification>()

        // try to statically convert a literal value into one of the desired type
        val literal = typecast.expression as? NumericLiteralValue
        if (literal != null) {
            val newLiteral = literal.cast(typecast.type)
            if (newLiteral !== literal)
                mods += IAstModification.ReplaceNode(typecast.expression, newLiteral, typecast)
        }

        // remove redundant nested typecasts:
        // if the typecast casts a value to the same type, remove the cast.
        // if the typecast contains another typecast, remove the inner typecast.
        val subTypecast = typecast.expression as? TypecastExpression
        if (subTypecast != null) {
            mods += IAstModification.ReplaceNode(typecast.expression, subTypecast.expression, typecast)
        } else {
            if (typecast.expression.inferType(program).istype(typecast.type))
                mods += IAstModification.ReplaceNode(typecast, typecast.expression, parent)
        }

        return mods
    }

    override fun before(expr: PrefixExpression, parent: Node): Iterable<IAstModification> {
        if (expr.operator == "+") {
            // +X --> X
            return listOf(IAstModification.ReplaceNode(expr, expr.expression, parent))
        } else if (expr.operator == "not") {
            when(expr.expression) {
                is PrefixExpression -> {
                    // NOT(NOT(...)) -> ...
                    val pe = expr.expression as PrefixExpression
                    if(pe.operator == "not")
                        return listOf(IAstModification.ReplaceNode(expr, pe.expression, parent))
                }
                is BinaryExpression -> {
                    // NOT (xxxx)  ->   invert the xxxx
                    val be = expr.expression as BinaryExpression
                    val newExpr = when (be.operator) {
                        "<" -> BinaryExpression(be.left, ">=", be.right, be.position)
                        ">" -> BinaryExpression(be.left, "<=", be.right, be.position)
                        "<=" -> BinaryExpression(be.left, ">", be.right, be.position)
                        ">=" -> BinaryExpression(be.left, "<", be.right, be.position)
                        "==" -> BinaryExpression(be.left, "!=", be.right, be.position)
                        "!=" -> BinaryExpression(be.left, "==", be.right, be.position)
                        else -> null
                    }

                    if (newExpr != null)
                        return listOf(IAstModification.ReplaceNode(expr, newExpr, parent))
                }
                else -> return emptyList()
            }
        }
        return emptyList()
    }

    override fun after(expr: BinaryExpression, parent: Node): Iterable<IAstModification> {
        val leftVal = expr.left.constValue(program)
        val rightVal = expr.right.constValue(program)

        val leftIDt = expr.left.inferType(program)
        val rightIDt = expr.right.inferType(program)
        if (!leftIDt.isKnown || !rightIDt.isKnown)
            throw FatalAstException("can't determine datatype of both expression operands $expr")

        // ConstValue <associativeoperator> X -->  X <associativeoperator> ConstValue
        if (leftVal != null && expr.operator in associativeOperators && rightVal == null)
            return listOf(IAstModification.SwapOperands(expr))

        // X + (-A)  -->  X - A
        if (expr.operator == "+" && (expr.right as? PrefixExpression)?.operator == "-") {
            return listOf(IAstModification.ReplaceNode(
                    expr,
                    BinaryExpression(expr.left, "-", (expr.right as PrefixExpression).expression, expr.position),
                    parent
            ))
        }

        // (-A) + X  -->  X - A
        if (expr.operator == "+" && (expr.left as? PrefixExpression)?.operator == "-") {
            return listOf(IAstModification.ReplaceNode(
                    expr,
                    BinaryExpression(expr.right, "-", (expr.left as PrefixExpression).expression, expr.position),
                    parent
            ))
        }

        // X - (-A)  -->  X + A
        if (expr.operator == "-" && (expr.right as? PrefixExpression)?.operator == "-") {
            return listOf(IAstModification.ReplaceNode(
                    expr,
                    BinaryExpression(expr.left, "+", (expr.right as PrefixExpression).expression, expr.position),
                    parent
            ))
        }

        val leftDt = leftIDt.typeOrElse(DataType.STRUCT)
        val rightDt = rightIDt.typeOrElse(DataType.STRUCT)

        if (expr.operator == "+" || expr.operator == "-"
                && leftVal == null && rightVal == null
                && leftDt in NumericDatatypes && rightDt in NumericDatatypes) {
            val leftBinExpr = expr.left as? BinaryExpression
            val rightBinExpr = expr.right as? BinaryExpression
            if (leftBinExpr?.operator == "*") {
                if (expr.operator == "+") {
                    // Y*X + X  ->  X*(Y + 1)
                    // X*Y + X  ->  X*(Y + 1)
                    val x = expr.right
                    val y = determineY(x, leftBinExpr)
                    if (y != null) {
                        val yPlus1 = BinaryExpression(y, "+", NumericLiteralValue(leftDt, 1, y.position), y.position)
                        val newExpr = BinaryExpression(x, "*", yPlus1, x.position)
                        return listOf(IAstModification.ReplaceNode(expr, newExpr, parent))
                    }
                } else {
                    // Y*X - X  ->  X*(Y - 1)
                    // X*Y - X  ->  X*(Y - 1)
                    val x = expr.right
                    val y = determineY(x, leftBinExpr)
                    if (y != null) {
                        val yMinus1 = BinaryExpression(y, "-", NumericLiteralValue(leftDt, 1, y.position), y.position)
                        val newExpr = BinaryExpression(x, "*", yMinus1, x.position)
                        return listOf(IAstModification.ReplaceNode(expr, newExpr, parent))
                    }
                }
            } else if (rightBinExpr?.operator == "*") {
                if (expr.operator == "+") {
                    // X + Y*X  ->  X*(Y + 1)
                    // X + X*Y  ->  X*(Y + 1)
                    val x = expr.left
                    val y = determineY(x, rightBinExpr)
                    if (y != null) {
                        val yPlus1 = BinaryExpression(y, "+", NumericLiteralValue.optimalInteger(1, y.position), y.position)
                        val newExpr = BinaryExpression(x, "*", yPlus1, x.position)
                        return listOf(IAstModification.ReplaceNode(expr, newExpr, parent))
                    }
                }
            }
        }

        if(expr.operator == ">=" && rightVal?.number == 0) {
            if (leftDt == DataType.UBYTE || leftDt == DataType.UWORD) {
                // unsigned >= 0 --> true
                return listOf(IAstModification.ReplaceNode(expr, NumericLiteralValue.fromBoolean(true, expr.position), parent))
            }
            when(leftDt) {
                DataType.BYTE -> {
                    // signed >=0   --> signed ^ $80
                    return listOf(IAstModification.ReplaceNode(
                            expr,
                            BinaryExpression(expr.left, "^", NumericLiteralValue.optimalInteger(0x80, expr.position), expr.position),
                            parent
                    ))
                }
                DataType.WORD -> {
                    // signedw >=0   --> msb(signedw) ^ $80
                    return listOf(IAstModification.ReplaceNode(
                            expr,
                            BinaryExpression(FunctionCall(IdentifierReference(listOf("msb"), expr.position),
                                    mutableListOf(expr.left),
                                    expr.position
                            ), "^", NumericLiteralValue.optimalInteger(0x80, expr.position), expr.position),
                            parent
                    ))
                }
                else -> {}
            }
        }

        if(expr.operator == "<" && rightVal?.number == 0) {
            if (leftDt == DataType.UBYTE || leftDt == DataType.UWORD) {
                // unsigned < 0 --> false
                return listOf(IAstModification.ReplaceNode(expr, NumericLiteralValue.fromBoolean(false, expr.position), parent))
            }
            when(leftDt) {
                DataType.BYTE -> {
                    // signed < 0  -->  signed & $80
                    return listOf(IAstModification.ReplaceNode(
                            expr,
                            BinaryExpression(expr.left, "&", NumericLiteralValue.optimalInteger(0x80, expr.position), expr.position),
                            parent
                    ))
                }
                DataType.WORD -> {
                    // signedw < 0 -->  msb(signedw) & $80
                    return listOf(IAstModification.ReplaceNode(
                            expr,
                            BinaryExpression(FunctionCall(IdentifierReference(listOf("msb"), expr.position),
                                    mutableListOf(expr.left),
                                    expr.position
                            ), "&", NumericLiteralValue.optimalInteger(0x80, expr.position), expr.position),
                            parent
                    ))
                }
                else -> {}
            }
        }

        // simplify when a term is constant and directly determines the outcome
        val constTrue = NumericLiteralValue.fromBoolean(true, expr.position)
        val constFalse = NumericLiteralValue.fromBoolean(false, expr.position)
        val newExpr: Expression? = when (expr.operator) {
            "or" -> {
                if ((leftVal != null && leftVal.asBooleanValue) || (rightVal != null && rightVal.asBooleanValue))
                    constTrue
                else if (leftVal != null && !leftVal.asBooleanValue)
                    expr.right
                else if (rightVal != null && !rightVal.asBooleanValue)
                    expr.left
                else
                    null
            }
            "and" -> {
                if ((leftVal != null && !leftVal.asBooleanValue) || (rightVal != null && !rightVal.asBooleanValue))
                    constFalse
                else if (leftVal != null && leftVal.asBooleanValue)
                    expr.right
                else if (rightVal != null && rightVal.asBooleanValue)
                    expr.left
                else
                    null
            }
            "xor" -> {
                if (leftVal != null && !leftVal.asBooleanValue)
                    expr.right
                else if (rightVal != null && !rightVal.asBooleanValue)
                    expr.left
                else if (leftVal != null && leftVal.asBooleanValue)
                    PrefixExpression("not", expr.right, expr.right.position)
                else if (rightVal != null && rightVal.asBooleanValue)
                    PrefixExpression("not", expr.left, expr.left.position)
                else
                    null
            }
            "|", "^" -> {
                if (leftVal != null && !leftVal.asBooleanValue)
                    expr.right
                else if (rightVal != null && !rightVal.asBooleanValue)
                    expr.left
                else
                    null
            }
            "&" -> {
                if (leftVal != null && !leftVal.asBooleanValue)
                    constFalse
                else if (rightVal != null && !rightVal.asBooleanValue)
                    constFalse
                else
                    null
            }
            "*" -> optimizeMultiplication(expr, leftVal, rightVal)
            "/" -> optimizeDivision(expr, leftVal, rightVal)
            "+" -> optimizeAdd(expr, leftVal, rightVal)
            "-" -> optimizeSub(expr, leftVal, rightVal)
            "**" -> optimizePower(expr, leftVal, rightVal)
            "%" -> optimizeRemainder(expr, leftVal, rightVal)
            ">>" -> optimizeShiftRight(expr, rightVal)
            "<<" -> optimizeShiftLeft(expr, rightVal)
            else -> null
        }

        if(newExpr != null)
            return listOf(IAstModification.ReplaceNode(expr, newExpr, parent))

        return emptyList()
    }

    private fun determineY(x: Expression, subBinExpr: BinaryExpression): Expression? {
        return when {
            subBinExpr.left isSameAs x -> subBinExpr.right
            subBinExpr.right isSameAs x -> subBinExpr.left
            else -> null
        }
    }

    private fun optimizeAdd(expr: BinaryExpression, leftVal: NumericLiteralValue?, rightVal: NumericLiteralValue?): Expression? {
        if(expr.left.isSameAs(expr.right)) {
            // optimize X+X into X *2
            expr.operator = "*"
            expr.right = NumericLiteralValue.optimalInteger(2, expr.right.position)
            expr.right.linkParents(expr)
            return expr
        }

        if (leftVal == null && rightVal == null)
            return null

        val (expr2, _, rightVal2) = reorderAssociative(expr, leftVal)
        if (rightVal2 != null) {
            // right value is a constant, see if we can optimize
            val rightConst: NumericLiteralValue = rightVal2
            when (rightConst.number.toDouble()) {
                0.0 -> {
                    // left
                    return expr2.left
                }
            }
        }
        // no need to check for left val constant (because of associativity)

        return null
    }

    private fun optimizeSub(expr: BinaryExpression, leftVal: NumericLiteralValue?, rightVal: NumericLiteralValue?): Expression? {
        if(expr.left.isSameAs(expr.right)) {
            // optimize X-X into 0
            return NumericLiteralValue.optimalInteger(0, expr.position)
        }

        if (leftVal == null && rightVal == null)
            return null

        if (rightVal != null) {
            // right value is a constant, see if we can optimize
            val rightConst: NumericLiteralValue = rightVal
            when (rightConst.number.toDouble()) {
                0.0 -> {
                    // left
                    return expr.left
                }
            }
        }
        if (leftVal != null) {
            // left value is a constant, see if we can optimize
            when (leftVal.number.toDouble()) {
                0.0 -> {
                    // -right
                    return PrefixExpression("-", expr.right, expr.position)
                }
            }
        }

        return null
    }

    private fun optimizePower(expr: BinaryExpression, leftVal: NumericLiteralValue?, rightVal: NumericLiteralValue?): Expression? {
        if (leftVal == null && rightVal == null)
            return null

        if (rightVal != null) {
            // right value is a constant, see if we can optimize
            val rightConst: NumericLiteralValue = rightVal
            when (rightConst.number.toDouble()) {
                -3.0 -> {
                    // -1/(left*left*left)
                    return BinaryExpression(NumericLiteralValue(DataType.FLOAT, -1.0, expr.position), "/",
                            BinaryExpression(expr.left, "*", BinaryExpression(expr.left, "*", expr.left, expr.position), expr.position),
                            expr.position)
                }
                -2.0 -> {
                    // -1/(left*left)
                    return BinaryExpression(NumericLiteralValue(DataType.FLOAT, -1.0, expr.position), "/",
                            BinaryExpression(expr.left, "*", expr.left, expr.position),
                            expr.position)
                }
                -1.0 -> {
                    // -1/left
                    return BinaryExpression(NumericLiteralValue(DataType.FLOAT, -1.0, expr.position), "/",
                            expr.left, expr.position)
                }
                0.0 -> {
                    // 1
                    return NumericLiteralValue(rightConst.type, 1, expr.position)
                }
                0.5 -> {
                    // sqrt(left)
                    return FunctionCall(IdentifierReference(listOf("sqrt"), expr.position), mutableListOf(expr.left), expr.position)
                }
                1.0 -> {
                    // left
                    return expr.left
                }
                2.0 -> {
                    // left*left
                    return BinaryExpression(expr.left, "*", expr.left, expr.position)
                }
                3.0 -> {
                    // left*left*left
                    return BinaryExpression(expr.left, "*", BinaryExpression(expr.left, "*", expr.left, expr.position), expr.position)
                }
            }
        }
        if (leftVal != null) {
            // left value is a constant, see if we can optimize
            when (leftVal.number.toDouble()) {
                -1.0 -> {
                    // -1
                    return NumericLiteralValue(DataType.FLOAT, -1.0, expr.position)
                }
                0.0 -> {
                    // 0
                    return NumericLiteralValue(leftVal.type, 0, expr.position)
                }
                1.0 -> {
                    //1
                    return NumericLiteralValue(leftVal.type, 1, expr.position)
                }

            }
        }

        return null
    }

    private fun optimizeRemainder(expr: BinaryExpression, leftVal: NumericLiteralValue?, rightVal: NumericLiteralValue?): Expression? {
        if (leftVal == null && rightVal == null)
            return null

        // simplify assignments  A = B <operator> C

        val cv = rightVal?.number?.toInt()?.toDouble()
        when (expr.operator) {
            "%" -> {
                if (cv == 1.0) {
                    return NumericLiteralValue(expr.inferType(program).typeOrElse(DataType.STRUCT), 0, expr.position)
                } else if (cv == 2.0) {
                    expr.operator = "&"
                    expr.right = NumericLiteralValue.optimalInteger(1, expr.position)
                    return null
                }
            }
        }
        return null

    }

    private fun optimizeDivision(expr: BinaryExpression, leftVal: NumericLiteralValue?, rightVal: NumericLiteralValue?): Expression? {
        if (leftVal == null && rightVal == null)
            return null

        // cannot shuffle assiciativity with division!
        if (rightVal != null) {
            // right value is a constant, see if we can optimize
            val rightConst: NumericLiteralValue = rightVal
            val cv = rightConst.number.toDouble()
            val leftIDt = expr.left.inferType(program)
            if (!leftIDt.isKnown)
                return null
            val leftDt = leftIDt.typeOrElse(DataType.STRUCT)
            when (cv) {
                -1.0 -> {
                    //  '/' -> -left
                    if (expr.operator == "/") {
                        return PrefixExpression("-", expr.left, expr.position)
                    }
                }
                1.0 -> {
                    //  '/' -> left
                    if (expr.operator == "/") {
                        return expr.left
                    }
                }
                in powersOfTwo -> {
                    if (leftDt in IntegerDatatypes) {
                        // divided by a power of two => shift right
                        val numshifts = log2(cv).toInt()
                        return BinaryExpression(expr.left, ">>", NumericLiteralValue.optimalInteger(numshifts, expr.position), expr.position)
                    }
                }
                in negativePowersOfTwo -> {
                    if (leftDt in IntegerDatatypes) {
                        // divided by a negative power of two => negate, then shift right
                        val numshifts = log2(-cv).toInt()
                        return BinaryExpression(PrefixExpression("-", expr.left, expr.position), ">>", NumericLiteralValue.optimalInteger(numshifts, expr.position), expr.position)
                    }
                }
            }

            if (leftDt == DataType.UBYTE) {
                if (abs(rightConst.number.toDouble()) >= 256.0) {
                    return NumericLiteralValue(DataType.UBYTE, 0, expr.position)
                }
            } else if (leftDt == DataType.UWORD) {
                if (abs(rightConst.number.toDouble()) >= 65536.0) {
                    return NumericLiteralValue(DataType.UBYTE, 0, expr.position)
                }
            }
        }

        if (leftVal != null) {
            // left value is a constant, see if we can optimize
            when (leftVal.number.toDouble()) {
                0.0 -> {
                    // 0
                    return NumericLiteralValue(leftVal.type, 0, expr.position)
                }
            }
        }

        return null
    }

    private fun optimizeMultiplication(expr: BinaryExpression, leftVal: NumericLiteralValue?, rightVal: NumericLiteralValue?): Expression? {
        if (leftVal == null && rightVal == null)
            return null

        val (expr2, _, rightVal2) = reorderAssociative(expr, leftVal)
        if (rightVal2 != null) {
            // right value is a constant, see if we can optimize
            val leftValue: Expression = expr2.left
            val rightConst: NumericLiteralValue = rightVal2
            when (val cv = rightConst.number.toDouble()) {
                -1.0 -> {
                    // -left
                    return PrefixExpression("-", leftValue, expr.position)
                }
                0.0 -> {
                    // 0
                    return NumericLiteralValue(rightConst.type, 0, expr.position)
                }
                1.0 -> {
                    // left
                    return expr2.left
                }
                in powersOfTwo -> {
                    if (leftValue.inferType(program).typeOrElse(DataType.STRUCT) in IntegerDatatypes) {
                        // times a power of two => shift left
                        val numshifts = log2(cv).toInt()
                        return BinaryExpression(expr2.left, "<<", NumericLiteralValue.optimalInteger(numshifts, expr.position), expr.position)
                    }
                }
                in negativePowersOfTwo -> {
                    if (leftValue.inferType(program).typeOrElse(DataType.STRUCT) in IntegerDatatypes) {
                        // times a negative power of two => negate, then shift left
                        val numshifts = log2(-cv).toInt()
                        return BinaryExpression(PrefixExpression("-", expr2.left, expr.position), "<<", NumericLiteralValue.optimalInteger(numshifts, expr.position), expr.position)
                    }
                }
            }
        }
        // no need to check for left val constant (because of associativity)

        return null
    }

    private fun optimizeShiftLeft(expr: BinaryExpression, amountLv: NumericLiteralValue?): Expression? {
        if (amountLv == null)
            return null

        val amount = amountLv.number.toInt()
        if (amount == 0) {
            return expr.left
        }
        val targetDt = expr.left.inferType(program).typeOrElse(DataType.STRUCT)
        when (targetDt) {
            DataType.UBYTE, DataType.BYTE -> {
                if (amount >= 8) {
                    return NumericLiteralValue(targetDt, 0, expr.position)
                }
            }
            DataType.UWORD, DataType.WORD -> {
                if (amount >= 16) {
                    return NumericLiteralValue(targetDt, 0, expr.position)
                } else if (amount >= 8) {
                    val lsb = TypecastExpression(expr.left, DataType.UBYTE, true, expr.position)
                    if (amount == 8) {
                        return FunctionCall(IdentifierReference(listOf("mkword"), expr.position), mutableListOf(NumericLiteralValue.optimalInteger(0, expr.position), lsb), expr.position)
                    }
                    val shifted = BinaryExpression(lsb, "<<", NumericLiteralValue.optimalInteger(amount - 8, expr.position), expr.position)
                    return FunctionCall(IdentifierReference(listOf("mkword"), expr.position), mutableListOf(NumericLiteralValue.optimalInteger(0, expr.position), shifted), expr.position)
                }
            }
            else -> {
            }
        }
        return null
    }

    private fun optimizeShiftRight(expr: BinaryExpression, amountLv: NumericLiteralValue?): Expression? {
        if (amountLv == null)
            return null

        val amount = amountLv.number.toInt()
        if (amount == 0) {
            return expr.left
        }
        val targetDt = expr.left.inferType(program).typeOrElse(DataType.STRUCT)
        when (targetDt) {
            DataType.UBYTE -> {
                if (amount >= 8) {
                    return NumericLiteralValue.optimalInteger(0, expr.position)
                }
            }
            DataType.BYTE -> {
                if (amount > 8) {
                    expr.right = NumericLiteralValue.optimalInteger(8, expr.right.position)
                    return null
                }
            }
            DataType.UWORD -> {
                if (amount >= 16) {
                    return NumericLiteralValue.optimalInteger(0, expr.position)
                } else if (amount >= 8) {
                    val msb = FunctionCall(IdentifierReference(listOf("msb"), expr.position), mutableListOf(expr.left), expr.position)
                    if (amount == 8)
                        return msb
                    return BinaryExpression(msb, ">>", NumericLiteralValue.optimalInteger(amount - 8, expr.position), expr.position)
                }
            }
            DataType.WORD -> {
                if (amount > 16) {
                    expr.right = NumericLiteralValue.optimalInteger(16, expr.right.position)
                    return null
                } else if (amount >= 8) {
                    val msbAsByte = TypecastExpression(
                            FunctionCall(IdentifierReference(listOf("msb"), expr.position), mutableListOf(expr.left), expr.position),
                            DataType.BYTE,
                            true, expr.position)
                    if (amount == 8)
                        return msbAsByte
                    return BinaryExpression(msbAsByte, ">>", NumericLiteralValue.optimalInteger(amount - 8, expr.position), expr.position)
                }
            }
            else -> {
            }
        }
        return null
    }

    private fun reorderAssociative(expr: BinaryExpression, leftVal: NumericLiteralValue?): ReorderedAssociativeBinaryExpr {
        if (expr.operator in associativeOperators && leftVal != null) {
            // swap left and right so that right is always the constant
            val tmp = expr.left
            expr.left = expr.right
            expr.right = tmp
            return ReorderedAssociativeBinaryExpr(expr, expr.right.constValue(program), leftVal)
        }
        return ReorderedAssociativeBinaryExpr(expr, leftVal, expr.right.constValue(program))
    }

    private data class ReorderedAssociativeBinaryExpr(val expr: BinaryExpression, val leftVal: NumericLiteralValue?, val rightVal: NumericLiteralValue?)

}
