package prog8.optimizer

import prog8.ast.IStatementContainer
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.FatalAstException
import prog8.ast.expressions.*
import prog8.ast.maySwapOperandOrder
import prog8.ast.statements.AnonymousScope
import prog8.ast.statements.Assignment
import prog8.ast.statements.IfElse
import prog8.ast.statements.Jump
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.code.core.*
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow

// TODO add more peephole expression optimizations? Investigate what optimizations binaryen has, also see  https://egorbo.com/peephole-optimizations.html

class ExpressionSimplifier(private val program: Program) : AstWalker() {
    private val powersOfTwo = (1..16).map { (2.0).pow(it) }.toSet()

    override fun after(typecast: TypecastExpression, parent: Node): Iterable<IAstModification> {
        val mods = mutableListOf<IAstModification>()

        // try to statically convert a literal value into one of the desired type
        val literal = typecast.expression as? NumericLiteral
        if (literal != null) {
            val newLiteral = literal.cast(typecast.type)
            if (newLiteral.isValid && newLiteral.valueOrZero() !== literal) {
                mods += IAstModification.ReplaceNode(typecast, newLiteral.valueOrZero(), parent)
            }
        }

        // remove redundant nested typecasts
        val subTypecast = typecast.expression as? TypecastExpression
        if (subTypecast != null) {
            // remove the sub-typecast if its datatype is larger than the outer typecast
            if(subTypecast.type largerThan typecast.type) {
                mods += IAstModification.ReplaceNode(typecast.expression, subTypecast.expression, typecast)
            }
        } else {
            if (typecast.expression.inferType(program) istype typecast.type) {
                // remove duplicate cast
                mods += IAstModification.ReplaceNode(typecast, typecast.expression, parent)
            }
        }

        return mods
    }

    override fun after(ifElse: IfElse, parent: Node): Iterable<IAstModification> {
        val truepart = ifElse.truepart
        val elsepart = ifElse.elsepart
        if(truepart.isNotEmpty() && elsepart.isNotEmpty()) {
            if(truepart.statements.singleOrNull() is Jump) {
                return listOf(
                    IAstModification.InsertAfter(ifElse, elsepart, parent as IStatementContainer),
                    IAstModification.ReplaceNode(elsepart, AnonymousScope(mutableListOf(), elsepart.position), ifElse)
                )
            }
            if(elsepart.statements.singleOrNull() is Jump) {
                val invertedCondition = invertCondition(ifElse.condition)
                if(invertedCondition!=null) {
                    return listOf(
                        IAstModification.ReplaceNode(ifElse.condition, invertedCondition, ifElse),
                        IAstModification.InsertAfter(ifElse, truepart, parent as IStatementContainer),
                        IAstModification.ReplaceNode(elsepart, AnonymousScope(mutableListOf(), elsepart.position), ifElse),
                        IAstModification.ReplaceNode(truepart, elsepart, ifElse)
                    )
                }
            }
        }
        return noModifications
    }

    override fun after(expr: BinaryExpression, parent: Node): Iterable<IAstModification> {
        val leftVal = expr.left.constValue(program)
        val rightVal = expr.right.constValue(program)

        val leftIDt = expr.left.inferType(program)
        val rightIDt = expr.right.inferType(program)
        if (!leftIDt.isKnown || !rightIDt.isKnown)
            throw FatalAstException("can't determine datatype of both expression operands $expr")

        // ConstValue <associativeoperator> X -->  X <associativeoperator> ConstValue
        if (leftVal != null && expr.operator in AssociativeOperators && rightVal == null && maySwapOperandOrder(expr))
            return listOf(IAstModification.SwapOperands(expr))

        // NonBinaryExpression  <associativeoperator>  BinaryExpression  -->  BinaryExpression  <associativeoperator>  NonBinaryExpression
        if (expr.operator in AssociativeOperators && expr.left !is BinaryExpression && expr.right is BinaryExpression) {
            if(parent !is Assignment || !(expr.left isSameAs parent.target) && maySwapOperandOrder(expr))
                return listOf(IAstModification.SwapOperands(expr))
        }

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

        val leftDt = leftIDt.getOr(DataType.UNDEFINED)
        val rightDt = rightIDt.getOr(DataType.UNDEFINED)

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
                        val yPlus1 = BinaryExpression(y, "+", NumericLiteral(leftDt, 1.0, y.position), y.position)
                        val newExpr = BinaryExpression(x, "*", yPlus1, x.position)
                        return listOf(IAstModification.ReplaceNode(expr, newExpr, parent))
                    }
                } else {
                    // Y*X - X  ->  X*(Y - 1)
                    // X*Y - X  ->  X*(Y - 1)
                    val x = expr.right
                    val y = determineY(x, leftBinExpr)
                    if (y != null) {
                        val yMinus1 = BinaryExpression(y, "-", NumericLiteral(leftDt, 1.0, y.position), y.position)
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
                        val yPlus1 = BinaryExpression(y, "+", NumericLiteral.optimalInteger(1, y.position), y.position)
                        val newExpr = BinaryExpression(x, "*", yPlus1, x.position)
                        return listOf(IAstModification.ReplaceNode(expr, newExpr, parent))
                    }
                }
            }
        }

        if(leftDt!=DataType.FLOAT && expr.operator == ">=" && rightVal?.number == 1.0) {
            // for integers: x >= 1  -->  x > 0
            expr.operator = ">"
            return listOf(IAstModification.ReplaceNode(expr.right, NumericLiteral.optimalInteger(0, expr.right.position), expr))
        }

        if(expr.operator == ">=" && rightVal?.number == 0.0) {
            if (leftDt == DataType.UBYTE || leftDt == DataType.UWORD) {
                // unsigned >= 0 --> true
                return listOf(IAstModification.ReplaceNode(expr, NumericLiteral.fromBoolean(true, expr.position), parent))
            }
        }

        if(leftDt!=DataType.FLOAT && expr.operator == "<" && rightVal?.number == 1.0) {
            // for integers: x < 1  -->  x <= 0
            expr.operator = "<="
            return listOf(IAstModification.ReplaceNode(expr.right, NumericLiteral.optimalInteger(0, expr.right.position), expr))
        }

        if(expr.operator == "<" && rightVal?.number == 0.0) {
            if (leftDt == DataType.UBYTE || leftDt == DataType.UWORD) {
                // unsigned < 0 --> false
                return listOf(IAstModification.ReplaceNode(expr, NumericLiteral.fromBoolean(false, expr.position), parent))
            }
        }

        // boolvar & 1  --> boolvar
        // boolvar & 2  --> false
        if(expr.operator=="&" && rightDt in IntegerDatatypes && (leftDt == DataType.BOOL || (expr.left as? TypecastExpression)?.expression?.inferType(program)?.istype(DataType.BOOL)==true)) {
            if(rightVal?.number==1.0) {
                return listOf(IAstModification.ReplaceNode(expr, expr.left, parent))
            } else if(rightVal?.number!=null && (rightVal.number.toInt() and 1)==0) {
                return listOf(IAstModification.ReplaceNode(expr, NumericLiteral.fromBoolean(false, expr.position), parent))
            }
        }

        // simplify when a term is constant and directly determines the outcome
        val constFalse = NumericLiteral.fromBoolean(false, expr.position)
        val newExpr: Expression? = when (expr.operator) {
            "|" -> {
                when {
                    leftVal?.number==0.0 -> expr.right
                    rightVal?.number==0.0 -> expr.left
                    rightIDt.isBytes && rightVal?.number==255.0 -> NumericLiteral(DataType.UBYTE, 255.0, rightVal.position)
                    rightIDt.isWords && rightVal?.number==65535.0 -> NumericLiteral(DataType.UWORD, 65535.0, rightVal.position)
                    leftIDt.isBytes && leftVal?.number==255.0 -> NumericLiteral(DataType.UBYTE, 255.0, leftVal.position)
                    leftIDt.isWords && leftVal?.number==65535.0 -> NumericLiteral(DataType.UWORD, 65535.0, leftVal.position)
                    else -> null
                }
            }
            "^" -> {
                when {
                    leftVal?.number==0.0 -> expr.right
                    rightVal?.number==0.0 -> expr.left
                    rightIDt.isBytes && rightVal?.number==255.0 -> PrefixExpression("~", expr.left, expr.left.position)
                    rightIDt.isWords && rightVal?.number==65535.0 -> PrefixExpression("~", expr.left, expr.left.position)
                    leftIDt.isBytes && leftVal?.number==255.0 -> PrefixExpression("~", expr.right, expr.right.position)
                    leftIDt.isWords && leftVal?.number==65535.0 -> PrefixExpression("~", expr.right, expr.right.position)
                    else -> null
                }
            }
            "&" -> {
                when {
                    leftVal?.number==0.0  -> constFalse
                    rightVal?.number==0.0 -> constFalse
                    rightIDt.isBytes && rightVal?.number==255.0 -> expr.left
                    rightIDt.isWords && rightVal?.number==65535.0 -> expr.left
                    leftIDt.isBytes && leftVal?.number==255.0 -> expr.right
                    leftIDt.isWords && leftVal?.number==65535.0 -> expr.right
                    else -> null
                }
            }
            "*" -> optimizeMultiplication(expr, leftVal, rightVal)
            "/" -> optimizeDivision(expr, leftVal, rightVal)
            "+" -> optimizeAdd(expr, leftVal, rightVal)
            "-" -> optimizeSub(expr, leftVal, rightVal)
            "%" -> optimizeRemainder(expr, leftVal, rightVal)
            ">>" -> optimizeShiftRight(expr, rightVal)
            "<<" -> optimizeShiftLeft(expr, rightVal)
            else -> null
        }

        if(rightVal!=null && leftDt==DataType.BOOL) {
            // see if we can replace comparison against true/1 with simpler comparison against zero
            if (expr.operator == "==") {
                if (rightVal.number == 1.0) {
                    val zero = NumericLiteral(DataType.UBYTE, 0.0, expr.right.position)
                    return listOf(
                        IAstModification.SetExpression({expr.operator="!="}, expr, parent),
                        IAstModification.ReplaceNode(expr.right, zero, expr)
                    )
                }
            }
            if (expr.operator == "!=") {
                if (rightVal.number == 1.0) {
                    val zero = NumericLiteral(DataType.UBYTE, 0.0, expr.right.position)
                    return listOf(
                        IAstModification.SetExpression({expr.operator="=="}, expr, parent),
                        IAstModification.ReplaceNode(expr.right, zero, expr)
                    )
                }
            }
        }

        if(newExpr != null)
            return listOf(IAstModification.ReplaceNode(expr, newExpr, parent))

        return noModifications
    }

    override fun after(functionCallExpr: FunctionCallExpression, parent: Node): Iterable<IAstModification> {
        if(functionCallExpr.target.nameInSource == listOf("lsb")) {
            if(functionCallExpr.args.isEmpty())
                return noModifications
            val arg = functionCallExpr.args[0]
            if(arg is TypecastExpression) {
                val valueDt = arg.expression.inferType(program)
                if (valueDt istype DataType.BYTE || valueDt istype DataType.UBYTE) {
                    // useless lsb() of byte value that was typecasted to word
                    return listOf(IAstModification.ReplaceNode(functionCallExpr, arg.expression, parent))
                }
            } else {
                if(arg is IdentifierReference && arg.nameInSource.size==2
                    && arg.nameInSource[0]=="cx16" && arg.nameInSource[1].uppercase() in RegisterOrPair.names) {
                    // lsb(cx16.r0) -> cx16.r0L
                    val highReg = IdentifierReference(listOf("cx16", arg.nameInSource[1]+'L'), arg.position)
                    return listOf(IAstModification.ReplaceNode(functionCallExpr, highReg, parent))
                }
                val argDt = arg.inferType(program)
                if (argDt istype DataType.BYTE || argDt istype DataType.UBYTE) {
                    // useless lsb() of byte value
                    return listOf(IAstModification.ReplaceNode(functionCallExpr, arg, parent))
                }
            }
        }
        else if(functionCallExpr.target.nameInSource == listOf("msb")) {
            if(functionCallExpr.args.isEmpty())
                return noModifications
            val arg = functionCallExpr.args[0]
            if(arg is TypecastExpression) {
                val valueDt = arg.expression.inferType(program)
                if (valueDt istype DataType.BYTE || valueDt istype DataType.UBYTE) {
                    // useless msb() of byte value that was typecasted to word, replace with 0
                    return listOf(IAstModification.ReplaceNode(
                            functionCallExpr,
                            NumericLiteral(valueDt.getOr(DataType.UBYTE), 0.0, arg.expression.position),
                            parent))
                }
            } else {
                if(arg is IdentifierReference && arg.nameInSource.size==2
                    && arg.nameInSource[0]=="cx16" && arg.nameInSource[1].uppercase() in RegisterOrPair.names) {
                    // msb(cx16.r0) -> cx16.r0H
                    val highReg = IdentifierReference(listOf("cx16", arg.nameInSource[1]+'H'), arg.position)
                    return listOf(IAstModification.ReplaceNode(functionCallExpr, highReg, parent))
                }
                val argDt = arg.inferType(program)
                if (argDt istype DataType.BYTE || argDt istype DataType.UBYTE) {
                    // useless msb() of byte value, replace with 0
                    return listOf(IAstModification.ReplaceNode(
                            functionCallExpr,
                            NumericLiteral(argDt.getOr(DataType.UBYTE), 0.0, arg.position),
                            parent))
                }
            }
        }

        return noModifications
    }

    private fun determineY(x: Expression, subBinExpr: BinaryExpression): Expression? {
        return when {
            subBinExpr.left isSameAs x -> subBinExpr.right
            subBinExpr.right isSameAs x -> subBinExpr.left
            else -> null
        }
    }

    private fun optimizeAdd(expr: BinaryExpression, leftVal: NumericLiteral?, rightVal: NumericLiteral?): Expression? {
        if(expr.left.isSameAs(expr.right)) {
            // optimize X+X into X *2
            expr.operator = "*"
            expr.right = NumericLiteral.optimalInteger(2, expr.right.position)
            expr.right.linkParents(expr)
            return expr
        }

        if (leftVal == null && rightVal == null)
            return null

        val (expr2, _, rightVal2) = reorderAssociativeWithConstant(expr, leftVal)
        if (rightVal2 != null) {
            // right value is a constant, see if we can optimize
            val rightConst: NumericLiteral = rightVal2
            when (rightConst.number) {
                0.0 -> {
                    // left
                    return expr2.left
                }
            }
        }
        // no need to check for left val constant (because of associativity)

        val rnum = rightVal?.number
        if(rnum!=null && rnum<0.0) {
            expr.operator = "-"
            expr.right = NumericLiteral(rightVal.type, -rnum, rightVal.position)
            return expr
        }

        return null
    }

    private fun optimizeSub(expr: BinaryExpression, leftVal: NumericLiteral?, rightVal: NumericLiteral?): Expression? {
        if(expr.left.isSameAs(expr.right)) {
            // optimize X-X into 0
            return NumericLiteral.optimalInteger(0, expr.position)
        }

        if (leftVal == null && rightVal == null)
            return null

        if (rightVal != null) {
            // right value is a constant, see if we can optimize
            val rnum = rightVal.number
            if (rnum == 0.0) {
                // left
                return expr.left
            }

            if(rnum<0.0) {
                expr.operator = "+"
                expr.right = NumericLiteral(rightVal.type, -rnum, rightVal.position)
                return expr
            }
        }
        if (leftVal != null) {
            // left value is a constant, see if we can optimize
            when (leftVal.number) {
                0.0 -> {
                    // -right
                    return PrefixExpression("-", expr.right, expr.position)
                }
            }
        }


        return null
    }

    private fun optimizeRemainder(expr: BinaryExpression, leftVal: NumericLiteral?, rightVal: NumericLiteral?): Expression? {
        if (leftVal == null && rightVal == null)
            return null

        // simplify assignments  A = B <operator> C

        val cv = rightVal?.number?.toInt()?.toDouble()
        when (expr.operator) {
            "%" -> {
                if (cv == 1.0) {
                    val idt = expr.inferType(program)
                    if(!idt.isKnown)
                        throw FatalAstException("unknown dt")
                    return NumericLiteral(idt.getOr(DataType.UNDEFINED), 0.0, expr.position)
                } else if (cv in powersOfTwo) {
                    expr.operator = "&"
                    expr.right = NumericLiteral.optimalInteger(cv!!.toInt()-1, expr.position)
                    return null
                }
            }
        }
        return null

    }

    private fun optimizeDivision(expr: BinaryExpression, leftVal: NumericLiteral?, rightVal: NumericLiteral?): Expression? {
        if (leftVal == null && rightVal == null)
            return null

        // cannot shuffle assiciativity with division!
        if (rightVal != null) {
            // right value is a constant, see if we can optimize
            val rightConst: NumericLiteral = rightVal
            val cv = rightConst.number
            val leftIDt = expr.left.inferType(program)
            if (!leftIDt.isKnown)
                return null
            val leftDt = leftIDt.getOr(DataType.UNDEFINED)
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
                    if (leftDt==DataType.UBYTE || leftDt==DataType.UWORD) {
                        // unsigned number divided by a power of two => shift right
                        val numshifts = log2(cv).toInt()
                        return BinaryExpression(expr.left, ">>", NumericLiteral.optimalInteger(numshifts, expr.position), expr.position)
                    }
                }
            }

            if (leftDt == DataType.UBYTE) {
                if (abs(rightConst.number) >= 256.0) {
                    return NumericLiteral(DataType.UBYTE, 0.0, expr.position)
                }
            } else if (leftDt == DataType.UWORD) {
                if (abs(rightConst.number) >= 65536.0) {
                    return NumericLiteral(DataType.UBYTE, 0.0, expr.position)
                }
            }
        }

        if (leftVal != null) {
            // left value is a constant, see if we can optimize
            when (leftVal.number) {
                0.0 -> {
                    // 0
                    return NumericLiteral(leftVal.type, 0.0, expr.position)
                }
            }
        }

        return null
    }

    private fun optimizeMultiplication(expr: BinaryExpression, leftVal: NumericLiteral?, rightVal: NumericLiteral?): Expression? {
        if (leftVal == null && rightVal == null)
            return null

        val (expr2, _, rightVal2) = reorderAssociativeWithConstant(expr, leftVal)
        if (rightVal2 != null) {
            // right value is a constant, see if we can optimize
            val leftValue: Expression = expr2.left
            val rightConst: NumericLiteral = rightVal2
            when (val cv = rightConst.number) {
                -1.0 -> {
                    // -left
                    return PrefixExpression("-", leftValue, expr.position)
                }
                0.0 -> {
                    // 0
                    return NumericLiteral(rightConst.type, 0.0, expr.position)
                }
                1.0 -> {
                    // left
                    return expr2.left
                }
                in powersOfTwo -> {
                    if (leftValue.inferType(program).isInteger) {
                        // times a power of two => shift left
                        val numshifts = log2(cv).toInt()
                        return BinaryExpression(expr2.left, "<<", NumericLiteral.optimalInteger(numshifts, expr.position), expr.position)
                    }
                }
            }
        }
        // no need to check for left val constant (because of associativity)

        return null
    }

    private fun optimizeShiftLeft(expr: BinaryExpression, amountLv: NumericLiteral?): Expression? {
        if (amountLv == null)
            return null

        val amount = amountLv.number.toInt()
        if (amount == 0) {
            return expr.left
        }
        val targetIDt = expr.left.inferType(program)
        if(!targetIDt.isKnown)
            throw FatalAstException("unknown dt")
        when (val targetDt = targetIDt.getOr(DataType.UNDEFINED)) {
            DataType.UBYTE, DataType.BYTE -> {
                if (amount >= 8) {
                    return NumericLiteral(targetDt, 0.0, expr.position)
                }
            }
            DataType.UWORD, DataType.WORD -> {
                if (amount >= 16) {
                    return NumericLiteral(targetDt, 0.0, expr.position)
                } else if (amount > 8) {
                    val lsb = FunctionCallExpression(IdentifierReference(listOf("lsb"), expr.position), mutableListOf(expr.left), expr.position)
                    val shifted = BinaryExpression(lsb, "<<", NumericLiteral.optimalInteger(amount - 8, expr.position), expr.position)
                    return FunctionCallExpression(IdentifierReference(listOf("mkword"), expr.position), mutableListOf(shifted, NumericLiteral.optimalInteger(0, expr.position)), expr.position)
                }
            }
            else -> {
            }
        }
        return null
    }

    private fun optimizeShiftRight(expr: BinaryExpression, amountLv: NumericLiteral?): Expression? {
        if (amountLv == null)
            return null

        val amount = amountLv.number.toInt()
        if (amount == 0) {
            return expr.left
        }
        val idt = expr.left.inferType(program)
        if(!idt.isKnown)
            throw FatalAstException("unknown dt")
        when (idt.getOr(DataType.UNDEFINED)) {
            DataType.UBYTE -> {
                if (amount >= 8) {
                    return NumericLiteral.optimalInteger(0, expr.position)
                }
            }
            DataType.BYTE -> {
                if (amount > 8) {
                    expr.right = NumericLiteral.optimalInteger(8, expr.right.position)
                    return null
                }
            }
            DataType.UWORD -> {
                if (amount >= 16) {
                    return NumericLiteral.optimalInteger(0, expr.position)
                }
                else if (amount > 8) {
                    val msb = FunctionCallExpression(IdentifierReference(listOf("msb"), expr.position), mutableListOf(expr.left), expr.position)
                    return TypecastExpression(BinaryExpression(msb, ">>", NumericLiteral.optimalInteger(amount - 8, expr.position), expr.position), DataType.UWORD, true, expr.position)
                }
            }
            DataType.WORD -> {
                if (amount > 16) {
                    expr.right = NumericLiteral.optimalInteger(16, expr.right.position)
                    return null
                }
            }
            else -> {
            }
        }
        return null
    }

    private fun reorderAssociativeWithConstant(expr: BinaryExpression, leftVal: NumericLiteral?): BinExprWithConstants {
        if (expr.operator in AssociativeOperators && leftVal != null && maySwapOperandOrder(expr)) {
            // swap left and right so that right is always the constant
            val tmp = expr.left
            expr.left = expr.right
            expr.right = tmp
            return BinExprWithConstants(expr, expr.right.constValue(program), leftVal)
        }
        return BinExprWithConstants(expr, leftVal, expr.right.constValue(program))
    }

    private data class BinExprWithConstants(val expr: BinaryExpression, val leftVal: NumericLiteral?, val rightVal: NumericLiteral?)

}