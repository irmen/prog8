package il65.functions

import il65.ast.IExpression
import il65.ast.INameScope
import il65.ast.LiteralValue
import il65.ast.Position

private fun oneDoubleArg(args: List<IExpression>, namespace: INameScope, function: (arg: Double)->Double): LiteralValue {
    if(args.size!=1)
        throw UnsupportedOperationException("built-in function requires one floating point argument")

    val float = args[0].constValue(namespace)?.asFloat()
    if(float!=null) {
        val result = LiteralValue(floatvalue = function(float))
        result.position = args[0].position
        return result
    }
    else
        throw UnsupportedOperationException("built-in function requires floating point value as argument")
}

private fun oneDoubleArgOutputInt(args: List<IExpression>, namespace: INameScope, function: (arg: Double)->Int): LiteralValue {
    if(args.size!=1)
        throw UnsupportedOperationException("built-in function requires one floating point argument")

    val float = args[0].constValue(namespace)?.asFloat()
    if(float!=null) {
        val result = LiteralValue(intvalue = function(float))
        result.position = args[0].position
        return result
    }
    else
        throw UnsupportedOperationException("built-in function requires floating point value as argument")
}

private fun twoDoubleArg(args: List<IExpression>, namespace: INameScope, function: (arg1: Double, arg2: Double)->Double): LiteralValue {
    if(args.size!=2)
        throw UnsupportedOperationException("built-in function requires two floating point arguments")

    val float1 = args[0].constValue(namespace)?.asFloat()
    val float2 = args[1].constValue(namespace)?.asFloat()
    if(float1!=null && float2!=null) {
        val result = LiteralValue(floatvalue = function(float1, float2))
        result.position = args[0].position
        return result
    }
    else
        throw UnsupportedOperationException("built-in function requires two floating point values as argument")
}

fun builtin_round(args: List<IExpression>, namespace: INameScope): LiteralValue
        = oneDoubleArgOutputInt(args, namespace) { it -> Math.round(it).toInt() }

fun builtin_sin(args: List<IExpression>, namespace: INameScope): LiteralValue
        = oneDoubleArg(args, namespace, Math::sin)

fun builtin_cos(args: List<IExpression>, namespace: INameScope): LiteralValue
        = oneDoubleArg(args, namespace, Math::cos)

fun builtin_acos(args: List<IExpression>, namespace: INameScope): LiteralValue
        = oneDoubleArg(args, namespace, Math::acos)

fun builtin_asin(args: List<IExpression>, namespace: INameScope): LiteralValue
        = oneDoubleArg(args, namespace, Math::asin)

fun builtin_tan(args: List<IExpression>, namespace: INameScope): LiteralValue
        = oneDoubleArg(args, namespace, Math::tan)

fun builtin_atan(args: List<IExpression>, namespace: INameScope): LiteralValue
        = oneDoubleArg(args, namespace, Math::atan)

fun builtin_log(args: List<IExpression>, namespace: INameScope): LiteralValue
        = oneDoubleArg(args, namespace, Math::log)

fun builtin_log10(args: List<IExpression>, namespace: INameScope): LiteralValue
        = oneDoubleArg(args, namespace, Math::log10)

fun builtin_sqrt(args: List<IExpression>, namespace: INameScope): LiteralValue
        = oneDoubleArg(args, namespace, Math::sqrt)

fun builtin_rad(args: List<IExpression>, namespace: INameScope): LiteralValue
        = oneDoubleArg(args, namespace, Math::toRadians)

fun builtin_deg(args: List<IExpression>, namespace: INameScope): LiteralValue
        = oneDoubleArg(args, namespace, Math::toDegrees)

fun builtin_abs(args: List<IExpression>, namespace: INameScope): LiteralValue {
    if(args.size!=1)
        throw UnsupportedOperationException("built-in function abs requires one numeric argument")
    val float = args[0].constValue(namespace)?.asFloat()
    if(float!=null)
        return IntOrFloatLiteral(Math.abs(float), args[0].position)
    else
        throw UnsupportedOperationException("built-in function abs requires floating point value as argument")
}

fun builtin_max(args: List<IExpression>, namespace: INameScope): LiteralValue {
    if(args.isEmpty())
        throw UnsupportedOperationException("max requires at least one argument")
    val constants = args.map { it.constValue(namespace) }
    if(constants.contains(null))
        throw UnsupportedOperationException("not all arguments to max are a constant value")
    val result = constants.map { it?.asFloat()!! }.max()
    return IntOrFloatLiteral(result!!, args[0].position)
}

fun builtin_min(args: List<IExpression>, namespace: INameScope): LiteralValue {
    if(args.isEmpty())
        throw UnsupportedOperationException("min requires at least one argument")
    val constants = args.map { it.constValue(namespace) }
    if(constants.contains(null))
        throw UnsupportedOperationException("not all arguments to min are a constant value")
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