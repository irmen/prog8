package il65.functions

import il65.ast.*


val BuiltIns = listOf(
        "sin", "cos", "abs", "acos", "asin", "tan", "atan", "log", "log10",
        "sqrt", "max", "min", "round", "rad", "deg", "avg", "sum"
)


class NotConstArgumentException: AstException("not a const argument to a built-in function")


private fun oneDoubleArg(args: List<IExpression>, position: Position?, namespace:INameScope, function: (arg: Double)->Number): LiteralValue {
    if(args.size!=1)
        throw SyntaxError("built-in function requires one floating point argument", position)

    val float = args[0].constValue(namespace)?.asFloat()
    if(float!=null) {
        val result = intOrFloatLiteral(function(float).toDouble(), args[0].position)
        result.position = args[0].position
        return result
    }
    else
        throw NotConstArgumentException()
}

private fun oneDoubleArgOutputInt(args: List<IExpression>, position: Position?, namespace:INameScope, function: (arg: Double)->Number): LiteralValue {
    if(args.size!=1)
        throw SyntaxError("built-in function requires one floating point argument", position)

    val float = args[0].constValue(namespace)?.asFloat()
    if(float!=null) {
        val result = LiteralValue(function(float).toInt())
        result.position = args[0].position
        return result
    }
    else
        throw NotConstArgumentException()
}

private fun oneIntArgOutputInt(args: List<IExpression>, position: Position?, namespace:INameScope, function: (arg: Int)->Number): LiteralValue {
    if(args.size!=1)
        throw SyntaxError("built-in function requires one integer argument", position)

    val integer = args[0].constValue(namespace)?.asInt()
    if(integer!=null) {
        val result = LiteralValue(function(integer).toInt())
        result.position = args[0].position
        return result
    }
    else
        throw NotConstArgumentException()
}

private fun nonScalarArgOutputNumber(args: List<IExpression>, position: Position?, namespace:INameScope,
                                     function: (arg: Collection<Double>)->Number): LiteralValue {
    if(args.size!=1)
        throw SyntaxError("builtin function requires one non-scalar argument", position)
    val iterable = args[0].constValue(namespace)
    if(iterable?.arrayvalue == null)
        throw SyntaxError("builtin function requires one non-scalar argument", position)
    val constants = iterable.arrayvalue.map { it.constValue(namespace) }
    if(constants.contains(null))
        throw NotConstArgumentException()
    val result = function(constants.map { it?.asFloat()!! }).toDouble()
    return intOrFloatLiteral(result, args[0].position)
}

private fun nonScalarArgOutputBoolean(args: List<IExpression>, position: Position?, namespace:INameScope,
                                      function: (arg: Collection<Double>)->Boolean): LiteralValue {
    if(args.size!=1)
        throw SyntaxError("builtin function requires one non-scalar argument", position)
    val iterable = args[0].constValue(namespace)
    if(iterable?.arrayvalue == null)
        throw SyntaxError("builtin function requires one non-scalar argument", position)
    val constants = iterable.arrayvalue.map { it.constValue(namespace) }
    if(constants.contains(null))
        throw NotConstArgumentException()
    val result = function(constants.map { it?.asFloat()!! })
    return LiteralValue(if(result) 1 else 0)
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

fun builtinAbs(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = oneDoubleArg(args, position, namespace, Math::abs)

fun builtinLsl(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = oneIntArgOutputInt(args, position, namespace) { x: Int -> x shl 1 }

fun builtinLsr(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = oneIntArgOutputInt(args, position, namespace) { x: Int -> x ushr 1 }

fun builtinMin(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = nonScalarArgOutputNumber(args, position, namespace) { it.min()!! }

fun builtinMax(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = nonScalarArgOutputNumber(args, position, namespace) { it.max()!! }

fun builtinSum(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = nonScalarArgOutputNumber(args, position, namespace) { it.sum() }

fun builtinAvg(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = nonScalarArgOutputNumber(args, position, namespace) { it.average() }

fun builtinLen(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = nonScalarArgOutputNumber(args, position, namespace) { it.size }

fun builtinAny(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = nonScalarArgOutputBoolean(args, position, namespace) { it.any { v -> v != 0.0} }

fun builtinAll(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = nonScalarArgOutputBoolean(args, position, namespace) { it.all { v -> v != 0.0} }


private fun intOrFloatLiteral(value: Double, position: Position?): LiteralValue {
    val intresult = value.toInt()
    val result = if(value-intresult==0.0)
        LiteralValue(intvalue = intresult)
    else
        LiteralValue(floatvalue = value)
    result.position = position
    return result
}