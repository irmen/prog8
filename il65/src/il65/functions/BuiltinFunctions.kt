package il65.functions

import il65.ast.*

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
        throw SyntaxError("built-in function requires floating point value as argument", position)
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
        throw SyntaxError("built-in function requires floating point value as argument", position)
}


fun builtin_round(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = oneDoubleArgOutputInt(args, position, namespace) { it -> Math.round(it).toInt() }

fun builtin_sin(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = oneDoubleArg(args, position, namespace, Math::sin)

fun builtin_cos(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = oneDoubleArg(args, position, namespace, Math::cos)

fun builtin_acos(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = oneDoubleArg(args, position, namespace, Math::acos)

fun builtin_asin(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = oneDoubleArg(args, position, namespace, Math::asin)

fun builtin_tan(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = oneDoubleArg(args, position, namespace, Math::tan)

fun builtin_atan(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = oneDoubleArg(args, position, namespace, Math::atan)

fun builtin_log(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = oneDoubleArg(args, position, namespace, Math::log)

fun builtin_log10(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = oneDoubleArg(args, position, namespace, Math::log10)

fun builtin_sqrt(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = oneDoubleArg(args, position, namespace, Math::sqrt)

fun builtin_rad(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = oneDoubleArg(args, position, namespace, Math::toRadians)

fun builtin_deg(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue
        = oneDoubleArg(args, position, namespace, Math::toDegrees)

fun builtin_abs(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue {
    if(args.size!=1)
        throw SyntaxError("built-in function abs requires one numeric argument", position)
    val float = args[0].constValue(namespace)?.asFloat()
    if(float!=null)
        return IntOrFloatLiteral(Math.abs(float), args[0].position)
    else
        throw SyntaxError("built-in function abs requires floating point value as argument", position)
}

fun builtin_max(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue {
    if(args.isEmpty())
        throw SyntaxError("max requires at least one argument", position)
    val constants = args.map { it.constValue(namespace) }
    if(constants.contains(null))
        throw SyntaxError("not all arguments to max are a constant value", position)
    val result = constants.map { it?.asFloat()!! }.max()
    return IntOrFloatLiteral(result!!, args[0].position)
}

fun builtin_min(args: List<IExpression>, position: Position?, namespace:INameScope): LiteralValue {
    if(args.isEmpty())
        throw SyntaxError("min requires at least one argument", position)
    val constants = args.map { it.constValue(namespace) }
    if(constants.contains(null))
        throw SyntaxError("not all arguments to min are a constant value", position)
    val result = constants.map { it?.asFloat()!! }.min()
    return IntOrFloatLiteral(result!!, args[0].position)
}


private fun IntOrFloatLiteral(value: Double, position: Position?): LiteralValue {
    val intresult = value.toInt()
    val result = if(value-intresult==0.0)
        LiteralValue(intvalue = intresult)
    else
        LiteralValue(floatvalue = value)
    result.position = position
    return result
}