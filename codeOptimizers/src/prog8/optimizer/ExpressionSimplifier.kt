package prog8.optimizer

import prog8.ast.*
import prog8.ast.base.FatalAstException
import prog8.ast.expressions.*
import prog8.ast.statements.AnonymousScope
import prog8.ast.statements.IfElse
import prog8.ast.statements.Jump
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.code.core.*
import kotlin.math.abs
import kotlin.math.log2


class ExpressionSimplifier(private val program: Program, private val errors: IErrorReporter) : AstWalker() {
    override fun after(typecast: TypecastExpression, parent: Node): Iterable<IAstModification> {
        val mods = mutableListOf<IAstModification>()

        // try to statically convert a literal value into one of the desired type
        val literal = typecast.expression as? NumericLiteral
        if (literal != null) {
            val newLiteral = literal.cast(typecast.type, typecast.implicit)
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
                val invertedCondition = invertCondition(ifElse.condition, program)
                return listOf(
                    IAstModification.ReplaceNode(ifElse.condition, invertedCondition, ifElse),
                    IAstModification.InsertAfter(ifElse, truepart, parent as IStatementContainer),
                    IAstModification.ReplaceNode(elsepart, AnonymousScope(mutableListOf(), elsepart.position), ifElse),
                    IAstModification.ReplaceNode(truepart, elsepart, ifElse)
                )
            }
        }

        val booleanCondition = ifElse.condition as? BinaryExpression
        if(booleanCondition!=null && booleanCondition.operator=="&") {
            val rightNum = booleanCondition.right as? NumericLiteral
            if (rightNum!=null && rightNum.type==DataType.UWORD) {
                if ((rightNum.number.toInt() and 0x00ff) == 0) {
                    // if WORD & $xx00  ->  if msb(WORD) & $xx
                    val msb = FunctionCallExpression(IdentifierReference(listOf("msb"), booleanCondition.left.position), mutableListOf(booleanCondition.left), booleanCondition.left.position)
                    val bytevalue = NumericLiteral(DataType.UBYTE, (rightNum.number.toInt() shr 8).toDouble(), booleanCondition.right.position)
                    return listOf(
                        IAstModification.ReplaceNode(booleanCondition.left, msb, booleanCondition),
                        IAstModification.ReplaceNode(booleanCondition.right, bytevalue, booleanCondition))
                }
                else if ((rightNum.number.toInt() and 0xff00) == 0) {
                    // if WORD & $00ff  ->  if lsb(WORD) & $ff
                    val lsb = FunctionCallExpression(IdentifierReference(listOf("lsb"), booleanCondition.left.position), mutableListOf(booleanCondition.left), booleanCondition.left.position)
                    val bytevalue = NumericLiteral(DataType.UBYTE, rightNum.number, booleanCondition.right.position)
                    return listOf(
                        IAstModification.ReplaceNode(booleanCondition.left, lsb, booleanCondition),
                        IAstModification.ReplaceNode(booleanCondition.right, bytevalue, booleanCondition))
                }
            }
        }

        return noModifications
    }

    override fun after(expr: BinaryExpression, parent: Node): Iterable<IAstModification> {
        val newExpr = applyAbsorptionLaws(expr)
        if(newExpr!=null)
            return listOf(IAstModification.ReplaceNode(expr, newExpr, parent))

        val leftVal = expr.left.constValue(program)
        val rightVal = expr.right.constValue(program)

        val leftIDt = expr.left.inferType(program)
        val rightIDt = expr.right.inferType(program)
        if (!leftIDt.isKnown || !rightIDt.isKnown)
            return noModifications

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
                        val replacement = BinaryExpression(x, "*", yPlus1, x.position)
                        return listOf(IAstModification.ReplaceNode(expr, replacement, parent))
                    }
                } else {
                    // Y*X - X  ->  X*(Y - 1)
                    // X*Y - X  ->  X*(Y - 1)
                    val x = expr.right
                    val y = determineY(x, leftBinExpr)
                    if (y != null) {
                        val yMinus1 = BinaryExpression(y, "-", NumericLiteral(leftDt, 1.0, y.position), y.position)
                        val replacement = BinaryExpression(x, "*", yMinus1, x.position)
                        return listOf(IAstModification.ReplaceNode(expr, replacement, parent))
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
                        val replacement = BinaryExpression(x, "*", yPlus1, x.position)
                        return listOf(IAstModification.ReplaceNode(expr, replacement, parent))
                    }
                }
            }
        }

        // X <= Y-1 ---> X<Y  ,  X >= Y+1 ---> X>Y
        if(leftDt in IntegerDatatypes && rightDt in IntegerDatatypes) {
            val rightExpr = expr.right as? BinaryExpression
            if(rightExpr!=null && rightExpr.right.constValue(program)?.number==1.0) {
                if (expr.operator == "<=" && rightExpr.operator == "-") {
                    expr.operator = "<"
                    return listOf(IAstModification.ReplaceNode(rightExpr, rightExpr.left, expr))
                } else if (expr.operator == ">=" && rightExpr.operator == "+") {
                    expr.operator = ">"
                    return listOf(IAstModification.ReplaceNode(rightExpr, rightExpr.left, expr))
                }
            }
        }

        if(leftDt!=DataType.FLOAT && expr.operator == ">=" && rightVal?.number == 1.0) {
            // for integers: x >= 1  -->  x > 0
            expr.operator = ">"
            return listOf(IAstModification.ReplaceNode(expr.right, NumericLiteral.optimalInteger(0, expr.right.position), expr))
        }

        // for signed integers: X <= -1 => X<0 ,  X > -1 => X>=0
        if(leftDt in SignedDatatypes && leftDt!=DataType.FLOAT && rightVal?.number==-1.0) {
            if(expr.operator=="<=") {
                expr.operator = "<"
                return listOf(IAstModification.ReplaceNode(expr.right, NumericLiteral(rightDt, 0.0, expr.right.position), expr))
            } else if(expr.operator==">") {
                expr.operator = ">="
                return listOf(IAstModification.ReplaceNode(expr.right, NumericLiteral(rightDt, 0.0, expr.right.position), expr))
            }
        }

        if (leftDt == DataType.UBYTE || leftDt == DataType.UWORD) {
            if(expr.operator == ">=" && rightVal?.number == 0.0) {
                // unsigned >= 0 --> true
                return listOf(IAstModification.ReplaceNode(expr, NumericLiteral.fromBoolean(true, expr.position), parent))
            }
            else if(expr.operator == ">" && rightVal?.number == 0.0) {
                // unsigned > 0 --> unsigned != 0
                return listOf(IAstModification.SetExpression({expr.operator="!="}, expr, parent))
            }
        }

        if(leftDt!=DataType.FLOAT && expr.operator == "<" && rightVal?.number == 1.0) {
            // for integers: x < 1  -->  x <= 0
            expr.operator = "<="
            return listOf(IAstModification.ReplaceNode(expr.right, NumericLiteral.optimalInteger(0, expr.right.position), expr))
        }

        if (leftDt == DataType.UBYTE || leftDt == DataType.UWORD) {
            if(expr.operator == "<" && rightVal?.number == 0.0) {
                // unsigned < 0 --> false
                return listOf(IAstModification.ReplaceNode(expr, NumericLiteral.fromBoolean(false, expr.position), parent))
            }
            else if(expr.operator == "<=" && rightVal?.number == 0.0) {
                // unsigned <= 0 --> unsigned==0
                return listOf(IAstModification.SetExpression({expr.operator="=="}, expr, parent))
            }
        }

        // optimize boolean constant comparisons
        if(expr.operator=="==") {
            if(rightDt==DataType.BOOL && leftDt==DataType.BOOL) {
                val rightConstBool = rightVal?.asBooleanValue
                if(rightConstBool==true) {
                    return listOf(IAstModification.ReplaceNode(expr, expr.left, parent))
                }
            }
            if (rightVal?.number == 1.0) {
                if (rightDt != leftDt) {
                    val right = NumericLiteral(leftDt, rightVal.number, rightVal.position)
                    return listOf(IAstModification.ReplaceNode(expr.right, right, expr))
                }
            }
            else if (rightVal?.number == 0.0) {
                if (rightDt != leftDt) {
                    val right = NumericLiteral(leftDt, rightVal.number, rightVal.position)
                    return listOf(IAstModification.ReplaceNode(expr.right, right, expr))
                }
            }
        }
        if (expr.operator=="!=") {
            if(rightDt==DataType.BOOL && leftDt==DataType.BOOL) {
                val rightConstBool = rightVal?.asBooleanValue
                if(rightConstBool==false) {
                    listOf(IAstModification.ReplaceNode(expr, expr.left, parent))
                }
            }
            if (rightVal?.number == 1.0) {
                if(rightDt!=leftDt) {
                    val right = NumericLiteral(leftDt, rightVal.number, rightVal.position)
                    return listOf(IAstModification.ReplaceNode(expr.right, right, expr))
                }
            }
            else if (rightVal?.number == 0.0) {
                if(rightDt!=leftDt) {
                    val right = NumericLiteral(leftDt, rightVal.number, rightVal.position)
                    return listOf(IAstModification.ReplaceNode(expr.right, right, expr))
                }
            }
        }

        if(expr.operator in arrayOf("and", "or", "xor")) {
            if(leftVal!=null) {
                val result = if(leftVal.asBooleanValue) {
                    when(expr.operator) {
                        "and" -> expr.right
                        "or" -> NumericLiteral.fromBoolean(true, expr.position)
                        "xor" -> PrefixExpression("not", expr.right, expr.position)
                        else -> throw FatalAstException("weird op")
                    }
                } else {
                    when(expr.operator) {
                        "and" -> NumericLiteral.fromBoolean(false, expr.position)
                        "or" -> expr.right
                        "xor" -> expr.right
                        else -> throw FatalAstException("weird op")
                    }
                }
                return listOf(IAstModification.ReplaceNode(expr, result, parent))
            }
            else if(rightVal!=null) {
                val result = if(rightVal.asBooleanValue) {
                    when(expr.operator) {
                        "and" -> expr.left
                        "or" -> NumericLiteral.fromBoolean(true, expr.position)
                        "xor" -> PrefixExpression("not", expr.left, expr.position)
                        else -> throw FatalAstException("weird op")
                    }
                } else {
                    when(expr.operator) {
                        "and" -> NumericLiteral.fromBoolean(false, expr.position)
                        "or" -> expr.left
                        "xor" -> expr.left
                        else -> throw FatalAstException("weird op")
                    }
                }
                return listOf(IAstModification.ReplaceNode(expr, result, parent))
            }
        }

        // simplify when a term is constant and directly determines the outcome
        val constFalse = NumericLiteral.fromBoolean(false, expr.position)
        val newExpr2 = when (expr.operator) {
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
            // boolean compare against a number -> just keep the boolean, no compare
            if(expr.operator=="==" || expr.operator=="!=") {
                val test = if (expr.operator == "==") rightVal.asBooleanValue else !rightVal.asBooleanValue
                return if (test) {
                    listOf(IAstModification.ReplaceNode(expr, expr.left, parent))
                } else {
                    listOf(IAstModification.ReplaceNode(expr, invertCondition(expr.left, program), parent))
                }
            }
        }

        if(newExpr2 != null)
            return listOf(IAstModification.ReplaceNode(expr, newExpr2, parent))

        if (rightVal!=null && (expr.operator == "==" || expr.operator == "!=")) {
            val bitwise = expr.left as? BinaryExpression
            if(bitwise!=null && bitwise.operator=="&" && bitwise.inferType(program).isWords) {
                val andNum = (bitwise.right as? NumericLiteral)?.number?.toInt()
                if (andNum!=null) {
                    if ((andNum and 0x00ff) == 0) {
                        // (WORD & $xx00)==y  ->  (msb(WORD) & $xx)==y
                        val msb = FunctionCallExpression(IdentifierReference(listOf("msb"), bitwise.left.position), mutableListOf(bitwise.left), bitwise.left.position)
                        val bytevalue = NumericLiteral(DataType.UBYTE, (andNum shr 8).toDouble(), bitwise.right.position)
                        val rightvalByte = NumericLiteral(DataType.UBYTE, (rightVal.number.toInt() shr 8).toDouble(), rightVal.position)
                        return listOf(
                            IAstModification.ReplaceNode(bitwise.left, msb, bitwise),
                            IAstModification.ReplaceNode(bitwise.right, bytevalue, bitwise),
                            IAstModification.ReplaceNode(expr.right, rightvalByte, expr)
                        )
                    }
                    else if((andNum and 0xff00) == 0) {
                        // (WORD & $00xx)==y  ->  (lsb(WORD) & $xx)==y
                        val lsb = FunctionCallExpression(IdentifierReference(listOf("lsb"), bitwise.left.position), mutableListOf(bitwise.left), bitwise.left.position)
                        val bytevalue = NumericLiteral(DataType.UBYTE, andNum.toDouble(), bitwise.right.position)
                        val rightvalByte = NumericLiteral(DataType.UBYTE, (rightVal.number.toInt() and 255).toDouble(), rightVal.position)
                        return listOf(
                            IAstModification.ReplaceNode(bitwise.left, lsb, bitwise),
                            IAstModification.ReplaceNode(bitwise.right, bytevalue, bitwise),
                            IAstModification.ReplaceNode(expr.right, rightvalByte, expr)
                        )
                    }
                }
            }
        }

        return noModifications
    }

    private fun applyAbsorptionLaws(expr: BinaryExpression): Expression? {
        // NOTE: only when the terms are not function calls!!!
        if(expr.left is IFunctionCall || expr.right is IFunctionCall)
            return null
        val rightB = expr.right as? BinaryExpression
        if(rightB!=null) {
            if(rightB.left is IFunctionCall || rightB.right is IFunctionCall)
                return null
            // absorption laws:  a or (a and b) --> a,  a and (a or b) --> a
            if(expr.operator=="or" && rightB.operator=="and") {
                if(expr.left isSameAs rightB.left || expr.left isSameAs rightB.right) {
                    return expr.left
                }
            }
            else if(expr.operator=="and" && rightB.operator=="or") {
                if(expr.left isSameAs rightB.left || expr.left isSameAs rightB.right) {
                    return expr.left
                }
            }
            else if(expr.operator=="or" && rightB.operator=="or") {
                if(expr.left isSameAs rightB.left || expr.left isSameAs rightB.right) {
                    // a or (a or b) -> a or b
                    return expr.right
                }
            }
            else if(expr.operator=="and" && rightB.operator=="and") {
                if(expr.left isSameAs rightB.left || expr.left isSameAs rightB.right) {
                    // a and (a and b) -> a and b
                    return expr.right
                }
            }
        }
        val leftB = expr.left as? BinaryExpression
        if(leftB!=null) {
            if(leftB.left is IFunctionCall || leftB.right is IFunctionCall)
                return null
            // absorption laws:  (a and b) or a --> a,  (a or b) and a --> a
            if(expr.operator=="or" && leftB.operator=="and") {
                if(expr.right isSameAs leftB.left || expr.right isSameAs leftB.right) {
                    return expr.right
                }
            }
            else if(expr.operator=="and" && leftB.operator=="or") {
                if(expr.right isSameAs leftB.left || expr.right isSameAs leftB.right) {
                    return expr.right
                }
            }
            else if(expr.operator=="or" && leftB.operator=="or") {
                if(expr.right isSameAs leftB.left || expr.right isSameAs leftB.right) {
                    // (a or b) or a -> a or b
                    return expr.left
                }
            }
            else if(expr.operator=="and" && leftB.operator=="and") {
                if(expr.right isSameAs leftB.left || expr.right isSameAs leftB.right) {
                    // (a and b) or a -> a and b
                    return expr.left
                }
            }
        }
        return null
    }

    override fun after(functionCallExpr: FunctionCallExpression, parent: Node): Iterable<IAstModification> {
        if(functionCallExpr.target.nameInSource == listOf("lsb")) {
            if(functionCallExpr.args.isEmpty())
                return noModifications
            val arg = functionCallExpr.args[0]
            if(arg is TypecastExpression) {
                val valueDt = arg.expression.inferType(program)
                if (valueDt istype DataType.UBYTE) {
                    // useless lsb() of ubyte value
                    return listOf(IAstModification.ReplaceNode(functionCallExpr, arg.expression, parent))
                }
                else if (valueDt istype DataType.BYTE) {
                    // useless lsb() of byte value, but as lsb() returns unsigned, we have to cast now.
                    val cast = TypecastExpression(arg.expression, DataType.UBYTE, true, arg.position)
                    return listOf(IAstModification.ReplaceNode(functionCallExpr, cast, parent))
                }
            } else {
                if(arg is IdentifierReference && arg.nameInSource.size==2
                    && arg.nameInSource[0]=="cx16" && arg.nameInSource[1].uppercase() in RegisterOrPair.names) {
                    // lsb(cx16.r0) -> cx16.r0L
                    val highReg = IdentifierReference(listOf("cx16", arg.nameInSource[1]+'L'), arg.position)
                    return listOf(IAstModification.ReplaceNode(functionCallExpr, highReg, parent))
                }
                val argDt = arg.inferType(program)
                if (argDt istype DataType.UBYTE) {
                    // useless lsb() of byte value
                    return listOf(IAstModification.ReplaceNode(functionCallExpr, arg, parent))
                }
                else if (argDt istype DataType.BYTE) {
                    // useless lsb() of byte value, but as lsb() returns unsigned, we have to cast now.
                    val cast = TypecastExpression(arg, DataType.UBYTE, true, arg.position)
                    return listOf(IAstModification.ReplaceNode(functionCallExpr, cast, parent))
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

        if(functionCallExpr.target.nameInSource == listOf("mkword")) {
            if(functionCallExpr.args[0].constValue(program)?.number==0.0) {
                // just cast the lsb to uword
                val cast = TypecastExpression(functionCallExpr.args[1], DataType.UWORD, true, functionCallExpr.position)
                return listOf(IAstModification.ReplaceNode(functionCallExpr, cast, parent))
            }
        }
        else if(functionCallExpr.target.nameInSource == listOf("strings", "contains")) {
            val target = (functionCallExpr.args[0] as? IdentifierReference)?.targetVarDecl(program)
            if(target?.value is StringLiteral) {
                errors.info("for actual strings, use a regular containment check instead: 'char in string'", functionCallExpr.position)
                val contains = ContainmentCheck(functionCallExpr.args[1], functionCallExpr.args[0], functionCallExpr.position)
                return listOf(IAstModification.ReplaceNode(functionCallExpr as Node, contains, parent))
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
                } else if (cv in powersOfTwoFloat) {
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
                0.0 -> return null // fall through to regular float division to properly deal with division by zero
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
                256.0 -> {
                    when(leftDt) {
                        DataType.UBYTE -> return NumericLiteral(DataType.UBYTE, 0.0, expr.position)
                        DataType.BYTE -> return null        // is either 0 or -1 we cannot tell here
                        DataType.UWORD, DataType.WORD -> {
                            // just use:  msb(value) as type
                            val msb = FunctionCallExpression(IdentifierReference(listOf("msb"), expr.position), mutableListOf(expr.left), expr.position)
                            return if(leftDt==DataType.WORD)
                                TypecastExpression(msb, DataType.BYTE, true, expr.position)
                            else
                                TypecastExpression(msb, DataType.UWORD, true, expr.position)
                        }
                        else -> return null
                    }
                }
                in powersOfTwoFloat -> {
                    val numshifts = powersOfTwoFloat.indexOf(cv)
                    if (leftDt in IntegerDatatypes) {
                        // division by a power of two => shift right (signed and unsigned)
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
                in powersOfTwoFloat -> {
                    if (leftValue.inferType(program).isInteger) {
                        // times a power of two => shift left
                        val numshifts = log2(cv).toInt()
                        return BinaryExpression(expr2.left, "<<", NumericLiteral.optimalInteger(numshifts, expr.position), expr.position)
                    }
                }
                in negativePowersOfTwoFloat -> {
                    if (leftValue.inferType(program).isInteger) {
                        // times a negative power of two => negate, then shift
                        val numshifts = log2(-cv).toInt()
                        val negation = PrefixExpression("-", expr2.left, expr.position)
                        return BinaryExpression(negation, "<<", NumericLiteral.optimalInteger(numshifts, expr.position), expr.position)
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
                    errors.warn("shift always results in 0", expr.position)
                    return NumericLiteral(targetDt, 0.0, expr.position)
                }
            }
            DataType.UWORD -> {
                if (amount >= 16) {
                    errors.warn("shift always results in 0", expr.position)
                    return NumericLiteral(targetDt, 0.0, expr.position)
                }
                else if(amount==8) {
                    // shift left by 8 bits is just a byte operation: mkword(lsb(X), 0)
                    val lsb = FunctionCallExpression(IdentifierReference(listOf("lsb"), expr.position), mutableListOf(expr.left), expr.position)
                    return FunctionCallExpression(IdentifierReference(listOf("mkword"), expr.position), mutableListOf(lsb, NumericLiteral(DataType.UBYTE, 0.0, expr.position)), expr.position)
                }
                else if (amount > 8) {
                    // same as above but with residual shifts.
                    val lsb = FunctionCallExpression(IdentifierReference(listOf("lsb"), expr.position), mutableListOf(expr.left), expr.position)
                    val shifted = BinaryExpression(lsb, "<<", NumericLiteral.optimalInteger(amount - 8, expr.position), expr.position)
                    return FunctionCallExpression(IdentifierReference(listOf("mkword"), expr.position), mutableListOf(shifted, NumericLiteral.optimalInteger(0, expr.position)), expr.position)
                }
            }
            DataType.WORD -> {
                if (amount >= 16) {
                    errors.warn("shift always results in 0", expr.position)
                    return NumericLiteral(targetDt, 0.0, expr.position)
                }
                else if(amount==8) {
                    // shift left by 8 bits is just a byte operation: mkword(lsb(X), 0)
                    val lsb = FunctionCallExpression(IdentifierReference(listOf("lsb"), expr.position), mutableListOf(expr.left), expr.position)
                    val mkword =  FunctionCallExpression(IdentifierReference(listOf("mkword"), expr.position), mutableListOf(lsb, NumericLiteral(DataType.UBYTE, 0.0, expr.position)), expr.position)
                    return TypecastExpression(mkword, DataType.WORD, true, expr.position)
                }
                else if (amount > 8) {
                    // same as above but with residual shifts.
                    val lsb = FunctionCallExpression(IdentifierReference(listOf("lsb"), expr.position), mutableListOf(expr.left), expr.position)
                    val shifted = BinaryExpression(lsb, "<<", NumericLiteral.optimalInteger(amount - 8, expr.position), expr.position)
                    val mkword = FunctionCallExpression(IdentifierReference(listOf("mkword"), expr.position), mutableListOf(shifted, NumericLiteral.optimalInteger(0, expr.position)), expr.position)
                    return TypecastExpression(mkword, DataType.WORD, true, expr.position)
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
                    errors.warn("shift always results in 0", expr.position)
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
                    errors.err("useless to shift by more than 15 bits", expr.position)
                    return null
                }
                else if(amount==8) {
                    // shift right by 8 bits is just a byte operation: msb(X) as uword
                    val msb = FunctionCallExpression(IdentifierReference(listOf("msb"), expr.position), mutableListOf(expr.left), expr.position)
                    return TypecastExpression(msb, DataType.UWORD, true, expr.position)
                }
                else if (amount > 8) {
                    // same as above but with residual shifts.
                    val msb = FunctionCallExpression(IdentifierReference(listOf("msb"), expr.position), mutableListOf(expr.left), expr.position)
                    return TypecastExpression(BinaryExpression(msb, ">>", NumericLiteral.optimalInteger(amount - 8, expr.position), expr.position), DataType.UWORD, true, expr.position)
                }
            }
            DataType.WORD -> {
                if (amount >= 16) {
                    errors.err("useless to shift by more than 15 bits", expr.position)
                    return null
                }
                else if(amount == 8) {
                    // shift right by 8 bits is just a byte operation: msb(X) as byte  (will get converted to word later)
                    val msb = FunctionCallExpression(IdentifierReference(listOf("msb"), expr.position), mutableListOf(expr.left), expr.position)
                    return TypecastExpression(msb, DataType.BYTE, true, expr.position)
                }
                else if(amount > 8) {
                    // same as above but with residual shifts. Take care to do signed shift.
                    val msb = FunctionCallExpression(IdentifierReference(listOf("msb"), expr.position), mutableListOf(expr.left), expr.position)
                    val signed = TypecastExpression(msb, DataType.BYTE, true, expr.position)
                    return BinaryExpression(signed, ">>", NumericLiteral.optimalInteger(amount - 8, expr.position), expr.position)
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