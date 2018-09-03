package il65.functions

import il65.ast.*


val BuiltIns = listOf("sin", "cos", "abs", "acos", "asin", "tan", "atan", "log", "log10", "sqrt", "max", "min", "round", "rad", "deg")


// @todo additional builtins such as: avg, sum, abs, round


class NotConstArgumentException: AstException("not a const argument to a built-in function")


private fun oneDoubleArg(args: List<IExpression>, position: Position?, namespace:INameScope, function: (arg: Double)->Double): LiteralValue {
    if(args.size!=1)
        throw SyntaxError("built-in function requires one floating point argument", position)

    val float = args[0].constValue(namespace)?.asFloat()
    if(float!=null) {
        val result = LiteralValue(floatvalue = function(float))
        result.position = args[0].position
        return result
    }
    else
        throw NotConstArgumentException()
}

private fun oneDoubleArgOutputInt(args: List<IExpression>, position: Position?, namespace:INameScope, function: (arg: Double)->Int): LiteralValue {
    if(args.size!=1)
        throw SyntaxError("built-in function requires one floating point argument", position)

    val float = args[0].constValue(namespace)?.asFloat()
    if(float!=null) {
        val result = LiteralValue(intvalue = function(float))
        result.position = args[0].position
        return result
    }
    else
        throw NotConstArgumentException()
}

private fun oneIntArgOutputInt(args: List<IExpression>, position: Position?, namespace:INameScope, function: (arg: Int)->Int): LiteralValue {
    if(args.size!=1)
        throw SyntaxError("built-in function requires one integer argument", position)

    val integer = args[0].constValue(namespace)?.asInt()
    if(integer!=null) {
        val result = LiteralValue(intvalue = function(integer))
        result.position = args[0].position
        return result
    }
    else
        throw NotConstArgumentException()
}

fun builtinRound(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = oneDoubleArgOutputInt(args, position, namespace) { it -> Math.round(it).toInt() }

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
    if(args.size!=1)
        throw SyntaxError("built-in function abs requires one numeric argument", position)
    val float = args[0].constValue(namespace)?.asFloat()
    if(float!=null)
        return intOrFloatLiteral(Math.abs(float), args[0].position)
    else
        throw SyntaxError("built-in function abs requires floating point value as argument", position)
}

fun builtinMax(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue {
    if(args.isEmpty())
        throw SyntaxError("max requires at least one argument", position)
    val constants = args.map { it.constValue(namespace) }
    if(constants.contains(null))
        throw NotConstArgumentException()
    val result = constants.map { it?.asFloat()!! }.max()
    return intOrFloatLiteral(result!!, args[0].position)
}

fun builtinMin(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue {
    if(args.isEmpty())
        throw SyntaxError("min requires at least one argument", position)
    val constants = args.map { it.constValue(namespace) }
    if(constants.contains(null))
        throw NotConstArgumentException()
    val result = constants.map { it?.asFloat()!! }.min()
    return intOrFloatLiteral(result!!, args[0].position)
}

fun builtinLsl(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue =
        oneIntArgOutputInt(args, position, namespace) { x: Int -> x shl 1 }

fun builtinLsr(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue =
        oneIntArgOutputInt(args, position, namespace) { x: Int -> x ushr 1 }


private fun intOrFloatLiteral(value: Double, position: Position?): LiteralValue {
    val intresult = value.toInt()
    val result = if(value-intresult==0.0)
        LiteralValue(intvalue = intresult)
    else
        LiteralValue(floatvalue = value)
    result.position = position
    return result
}