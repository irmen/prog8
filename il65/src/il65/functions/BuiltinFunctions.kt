package il65.functions

import il65.ast.IExpression
import il65.ast.INameScope
import il65.ast.LiteralValue

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

fun builtin_round(args: List<IExpression>, namespace: INameScope): LiteralValue = oneDoubleArgOutputInt(args, namespace) { it -> Math.round(it).toInt() }
fun builtin_sin(args: List<IExpression>, namespace: INameScope): LiteralValue = oneDoubleArg(args, namespace, Math::sin)
fun builtin_cos(args: List<IExpression>, namespace: INameScope): LiteralValue = oneDoubleArg(args, namespace, Math::cos)
fun builtin_abs(args: List<IExpression>, namespace: INameScope): LiteralValue = oneDoubleArg(args, namespace, Math::abs)
fun builtin_acos(args: List<IExpression>, namespace: INameScope): LiteralValue = oneDoubleArg(args, namespace, Math::acos)
fun builtin_asin(args: List<IExpression>, namespace: INameScope): LiteralValue = oneDoubleArg(args, namespace, Math::asin)
fun builtin_tan(args: List<IExpression>, namespace: INameScope): LiteralValue = oneDoubleArg(args, namespace, Math::tan)
fun builtin_atan(args: List<IExpression>, namespace: INameScope): LiteralValue = oneDoubleArg(args, namespace, Math::atan)
fun builtin_log(args: List<IExpression>, namespace: INameScope): LiteralValue = oneDoubleArg(args, namespace, Math::log)
fun builtin_log10(args: List<IExpression>, namespace: INameScope): LiteralValue = oneDoubleArg(args, namespace, Math::log10)
fun builtin_sqrt(args: List<IExpression>, namespace: INameScope): LiteralValue = oneDoubleArg(args, namespace, Math::sqrt)
fun builtin_max(args: List<IExpression>, namespace: INameScope): LiteralValue = twoDoubleArg(args, namespace, Math::max)
fun builtin_min(args: List<IExpression>, namespace: INameScope): LiteralValue = twoDoubleArg(args, namespace, Math::min)
