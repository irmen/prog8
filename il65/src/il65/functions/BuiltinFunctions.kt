package il65.functions

import il65.ast.*
import kotlin.math.abs
import kotlin.math.floor


val BuiltinFunctionNames = setOf(
        "P_carry", "P_irqd", "rol", "ror", "rol2", "ror2", "lsl", "lsr",
        "sin", "cos", "abs", "acos", "asin", "tan", "atan",
        "log", "log10", "sqrt", "rad", "deg", "round", "floor", "ceil",
        "max", "min", "avg", "sum", "len", "any", "all", "lsb", "msb")

val BuiltinFunctionsWithoutSideEffects = BuiltinFunctionNames - setOf("P_carry", "P_irqd")


class NotConstArgumentException: AstException("not a const argument to a built-in function")


private fun oneDoubleArg(args: List<IExpression>, position: Position?, namespace:INameScope, function: (arg: Double)->Number): LiteralValue {
    if(args.size!=1)
        throw SyntaxError("built-in function requires one floating point argument", position)
    val constval = args[0].constValue(namespace) ?: throw NotConstArgumentException()
    if(!constval.isFloat)
        throw SyntaxError("built-in function requires one floating point argument", position)

    val float = constval.asNumericValue?.toDouble()!!
    return numericLiteral(function(float), args[0].position)
}

private fun oneDoubleArgOutputInt(args: List<IExpression>, position: Position?, namespace:INameScope, function: (arg: Double)->Number): LiteralValue {
    if(args.size!=1)
        throw SyntaxError("built-in function requires one floating point argument", position)
    val constval = args[0].constValue(namespace) ?: throw NotConstArgumentException()
    if(!constval.isFloat)
        throw SyntaxError("built-in function requires one floating point argument", position)

    val float = constval.asNumericValue?.toDouble()!!
    return numericLiteral(function(float).toInt(), args[0].position)
}

private fun oneIntArgOutputInt(args: List<IExpression>, position: Position?, namespace:INameScope, function: (arg: Int)->Number): LiteralValue {
    if(args.size!=1)
        throw SyntaxError("built-in function requires one integer argument", position)
    val constval = args[0].constValue(namespace) ?: throw NotConstArgumentException()
    if(!constval.isInteger)
        throw SyntaxError("built-in function requires one integer argument", position)

    val integer = constval.asNumericValue?.toInt()!!
    return numericLiteral(function(integer).toInt(), args[0].position)
}

private fun collectionArgOutputNumber(args: List<IExpression>, position: Position?, namespace:INameScope,
                                      function: (arg: Collection<Double>)->Number): LiteralValue {
    if(args.size!=1)
        throw SyntaxError("builtin function requires one non-scalar argument", position)
    val iterable = args[0].constValue(namespace)
    if(iterable?.arrayvalue == null)
        throw SyntaxError("builtin function requires one non-scalar argument", position)
    val constants = iterable.arrayvalue.map { it.constValue(namespace)?.asNumericValue }
    if(constants.contains(null))
        throw NotConstArgumentException()
    val result = function(constants.map { it!!.toDouble() }).toDouble()
    return numericLiteral(result, args[0].position)
}

private fun collectionArgOutputBoolean(args: List<IExpression>, position: Position?, namespace:INameScope,
                                       function: (arg: Collection<Double>)->Boolean): LiteralValue {
    if(args.size!=1)
        throw SyntaxError("builtin function requires one non-scalar argument", position)
    val iterable = args[0].constValue(namespace)
    if(iterable?.arrayvalue == null)
        throw SyntaxError("builtin function requires one non-scalar argument", position)
    val constants = iterable.arrayvalue.map { it.constValue(namespace)?.asNumericValue }
    if(constants.contains(null))
        throw NotConstArgumentException()
    val result = function(constants.map { it?.toDouble()!! })
    return LiteralValue(bytevalue = if(result) 1 else 0)
}

fun builtinRound(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = oneDoubleArgOutputInt(args, position, namespace, Math::round)

fun builtinFloor(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = oneDoubleArgOutputInt(args, position, namespace, Math::floor)

fun builtinCeil(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = oneDoubleArgOutputInt(args, position, namespace, Math::ceil)

fun builtinSin(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = oneDoubleArg(args, position, namespace, Math::sin)

fun builtinCos(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = oneDoubleArg(args, position, namespace, Math::cos)

fun builtinAcos(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = oneDoubleArg(args, position, namespace, Math::acos)

fun builtinAsin(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = oneDoubleArg(args, position, namespace, Math::asin)

fun builtinTan(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = oneDoubleArg(args, position, namespace, Math::tan)

fun builtinAtan(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = oneDoubleArg(args, position, namespace, Math::atan)

fun builtinLog(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = oneDoubleArg(args, position, namespace, Math::log)

fun builtinLog10(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = oneDoubleArg(args, position, namespace, Math::log10)

fun builtinSqrt(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = oneDoubleArg(args, position, namespace, Math::sqrt)

fun builtinRad(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = oneDoubleArg(args, position, namespace, Math::toRadians)

fun builtinDeg(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = oneDoubleArg(args, position, namespace, Math::toDegrees)

fun builtinAbs(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue {
    // 1 arg, type = float or int, result type= same as argument type
    if(args.size!=1)
        throw SyntaxError("abs requires one numeric argument", position)

    val constval = args[0].constValue(namespace) ?: throw NotConstArgumentException()
    val number = constval.asNumericValue
    return when (number) {
        is Int, is Byte, is Short -> numericLiteral(abs(number.toInt()), args[0].position)
        is Double -> numericLiteral(abs(number.toDouble()), args[0].position)
        else -> throw SyntaxError("abs requires one numeric argument", position)
    }
}


// todo different functions for byte/word params/results?
fun builtinLsb(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = oneIntArgOutputInt(args, position, namespace) { x: Int -> x and 255 }

fun builtinMsb(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = oneIntArgOutputInt(args, position, namespace) { x: Int -> x ushr 8 and 255}

fun builtinLsl(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = oneIntArgOutputInt(args, position, namespace) { x: Int -> x shl 1 }

fun builtinLsr(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = oneIntArgOutputInt(args, position, namespace) { x: Int -> x ushr 1 }

fun builtinMin(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = collectionArgOutputNumber(args, position, namespace) { it.min()!! }

fun builtinMax(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = collectionArgOutputNumber(args, position, namespace) { it.max()!! }

fun builtinSum(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = collectionArgOutputNumber(args, position, namespace) { it.sum() }

fun builtinAvg(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue {
    if(args.size!=1)
        throw SyntaxError("avg requires one non-scalar argument", position)
    val iterable = args[0].constValue(namespace)
    if(iterable?.arrayvalue == null)
        throw SyntaxError("avg requires one non-scalar argument", position)
    val constants = iterable.arrayvalue.map { it.constValue(namespace)?.asNumericValue }
    if(constants.contains(null))
        throw NotConstArgumentException()
    val result = (constants.map { it!!.toDouble() }).average()
    return numericLiteral(result, args[0].position)
}

fun builtinLen(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue {
    if(args.size!=1)
        throw SyntaxError("len requires one argument", position)
    val argument = args[0].constValue(namespace) ?: throw NotConstArgumentException()
    return when {
        argument.isArray -> numericLiteral(argument.arrayvalue!!.size, args[0].position)
        argument.isString -> numericLiteral(argument.strvalue!!.length, args[0].position)
        else -> throw FatalAstException("len of weird argument ${args[0]}")
    }
}

fun builtinAny(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = collectionArgOutputBoolean(args, position, namespace) { it.any { v -> v != 0.0} }

fun builtinAll(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = collectionArgOutputBoolean(args, position, namespace) { it.all { v -> v != 0.0} }


private fun numericLiteral(value: Number, position: Position?): LiteralValue {
    val floatNum=value.toDouble()
    val tweakedValue: Number =
            if(floatNum==floor(floatNum) && floatNum in -32768..65535)
                floatNum.toInt()  // we have an integer disguised as a float.
            else
                floatNum

    val result = when(tweakedValue) {
        is Int -> LiteralValue.optimalNumeric(value.toInt())
        is Short -> LiteralValue.optimalNumeric(value.toInt())
        is Byte -> LiteralValue(bytevalue = value.toShort())
        is Double -> LiteralValue(floatvalue = value.toDouble())
        is Float -> LiteralValue(floatvalue = value.toDouble())
        else -> throw FatalAstException("invalid number type ${value::class}")
    }
    result.position = position
    return result
}
