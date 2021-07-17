package prog8.optimizer

import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.Assignment
import prog8.ast.statements.ForLoop
import prog8.ast.statements.VarDecl
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.compiler.target.ICompilationTarget
import kotlin.math.pow


internal class ConstantFoldingOptimizer(private val program: Program, private val compTarget: ICompilationTarget) : AstWalker() {

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
                    DataType.BYTE -> {
                        listOf(IAstModification.ReplaceNode(expr,
                                NumericLiteralValue(DataType.BYTE, subexpr.number.toInt().inv(), subexpr.position),
                                parent))
                    }
                    DataType.UBYTE -> {
                        listOf(IAstModification.ReplaceNode(expr,
                                NumericLiteralValue(DataType.UBYTE, subexpr.number.toInt().inv() and 255, subexpr.position),
                                parent))
                    }
                    DataType.WORD -> {
                        listOf(IAstModification.ReplaceNode(expr,
                                NumericLiteralValue(DataType.WORD, subexpr.number.toInt().inv(), subexpr.position),
                                parent))
                    }
                    DataType.UWORD -> {
                        listOf(IAstModification.ReplaceNode(expr,
                                NumericLiteralValue(DataType.UWORD, subexpr.number.toInt().inv() and 65535, subexpr.position),
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
        val modifications = mutableListOf<IAstModification>()

        if(expr.operator == "**" && leftconst!=null) {
            // optimize various simple cases of ** :
            //  optimize away 1 ** x into just 1 and 0 ** x into just 0
            //  optimize 2 ** x into (1<<x)  if both operands are integer.
            val leftDt = leftconst.inferType(program).typeOrElse(DataType.UNDEFINED)
            when (leftconst.number.toDouble()) {
                0.0 -> {
                    val value = NumericLiteralValue(leftDt, 0, expr.position)
                    modifications += IAstModification.ReplaceNode(expr, value, parent)
                }
                1.0 -> {
                    val value = NumericLiteralValue(leftDt, 1, expr.position)
                    modifications += IAstModification.ReplaceNode(expr, value, parent)
                }
                2.0 -> {
                    if(rightconst!=null) {
                        val value = NumericLiteralValue(leftDt, 2.0.pow(rightconst.number.toDouble()), expr.position)
                        modifications += IAstModification.ReplaceNode(expr, value, parent)
                    } else {
                        val rightDt = expr.right.inferType(program).typeOrElse(DataType.UNDEFINED)
                        if(leftDt in IntegerDatatypes && rightDt in IntegerDatatypes) {
                            val targetDt =
                                when (parent) {
                                    is Assignment -> parent.target.inferType(program).typeOrElse(DataType.UNDEFINED)
                                    is VarDecl -> parent.datatype
                                    else -> leftDt
                                }
                            val one = NumericLiteralValue(targetDt, 1, expr.position)
                            val shift = BinaryExpression(one, "<<", expr.right, expr.position)
                            modifications += IAstModification.ReplaceNode(expr, shift, parent)
                        }
                    }
                }
            }
        }

        if(expr.inferType(program).istype(DataType.FLOAT)) {
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

        // const fold when both operands are a const
        if(leftconst != null && rightconst != null) {
            val evaluator = ConstExprEvaluator()
            val result = evaluator.evaluate(leftconst, expr.operator, rightconst)
            modifications += IAstModification.ReplaceNode(expr, result, parent)
        }

        return modifications
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
                val newArray = array.cast(arrayDt.typeOrElse(DataType.UNDEFINED))
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
        fun adjustRangeDt(rangeFrom: NumericLiteralValue, targetDt: DataType, rangeTo: NumericLiteralValue, stepLiteral: NumericLiteralValue?, range: RangeExpr): RangeExpr? {
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

            return RangeExpr(fromCast.valueOrZero(), toCast.valueOrZero(), newStep, range.position)
        }

        // adjust the datatype of a range expression in for loops to the loop variable.
        val iterableRange = forLoop.iterable as? RangeExpr ?: return noModifications
        val rangeFrom = iterableRange.from as? NumericLiteralValue
        val rangeTo = iterableRange.to as? NumericLiteralValue
        if(rangeFrom==null || rangeTo==null) return noModifications

        val loopvar = forLoop.loopVar.targetVarDecl(program) ?: throw UndefinedSymbolError(forLoop.loopVar)

        val stepLiteral = iterableRange.step as? NumericLiteralValue
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
        val numval = decl.value as? NumericLiteralValue
        if(decl.type== VarDeclType.CONST && numval!=null) {
            val valueDt = numval.inferType(program)
            if(!valueDt.istype(decl.datatype)) {
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
