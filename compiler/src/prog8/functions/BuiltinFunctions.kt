package prog8.functions

import prog8.ast.*
import prog8.compiler.HeapValues
import kotlin.math.log2



class BuiltinFunctionParam(val name: String, val possibleDatatypes: List<DataType>)

class FunctionSignature(val pure: Boolean,      // does it have side effects?
                        val parameters: List<BuiltinFunctionParam>,
                        val returntype: DataType?,
                        val constExpressionFunc: ((args: List<IExpression>, position: Position, namespace: INameScope, heap: HeapValues) -> LiteralValue)? = null)


val BuiltinFunctions = mapOf(
    "rol"         to FunctionSignature(false, listOf(BuiltinFunctionParam("item", listOf(DataType.BYTE, DataType.WORD))), null),
    "ror"         to FunctionSignature(false, listOf(BuiltinFunctionParam("item", listOf(DataType.BYTE, DataType.WORD))), null),
    "rol2"        to FunctionSignature(false, listOf(BuiltinFunctionParam("item", listOf(DataType.BYTE, DataType.WORD))), null),
    "ror2"        to FunctionSignature(false, listOf(BuiltinFunctionParam("item", listOf(DataType.BYTE, DataType.WORD))), null),
    "lsl"         to FunctionSignature(false, listOf(BuiltinFunctionParam("item", listOf(DataType.BYTE, DataType.WORD))), null),
    "lsr"         to FunctionSignature(false, listOf(BuiltinFunctionParam("item", listOf(DataType.BYTE, DataType.WORD))), null),
    "sin"         to FunctionSignature(true, listOf(BuiltinFunctionParam("rads", listOf(DataType.FLOAT))), DataType.FLOAT) { a, p, n, h -> oneDoubleArg(a, p, n, h, Math::sin) },
    "cos"         to FunctionSignature(true, listOf(BuiltinFunctionParam("rads", listOf(DataType.FLOAT))), DataType.FLOAT) { a, p, n, h -> oneDoubleArg(a, p, n, h, Math::cos) },
    "acos"        to FunctionSignature(true, listOf(BuiltinFunctionParam("rads", listOf(DataType.FLOAT))), DataType.FLOAT) { a, p, n, h -> oneDoubleArg(a, p, n, h, Math::acos) },
    "asin"        to FunctionSignature(true, listOf(BuiltinFunctionParam("rads", listOf(DataType.FLOAT))), DataType.FLOAT) { a, p, n, h -> oneDoubleArg(a, p, n, h, Math::asin) },
    "tan"         to FunctionSignature(true, listOf(BuiltinFunctionParam("rads", listOf(DataType.FLOAT))), DataType.FLOAT) { a, p, n, h -> oneDoubleArg(a, p, n, h, Math::tan) },
    "atan"        to FunctionSignature(true, listOf(BuiltinFunctionParam("rads", listOf(DataType.FLOAT))), DataType.FLOAT) { a, p, n, h -> oneDoubleArg(a, p, n, h, Math::atan) },
    "ln"          to FunctionSignature(true, listOf(BuiltinFunctionParam("value", listOf(DataType.FLOAT))), DataType.FLOAT) { a, p, n, h -> oneDoubleArg(a, p, n, h, Math::log) },
    "log2"        to FunctionSignature(true, listOf(BuiltinFunctionParam("value", listOf(DataType.FLOAT))), DataType.FLOAT) { a, p, n, h -> oneDoubleArg(a, p, n, h, ::log2) },
    "log10"       to FunctionSignature(true, listOf(BuiltinFunctionParam("value", listOf(DataType.FLOAT))), DataType.FLOAT) { a, p, n, h -> oneDoubleArg(a, p, n, h, Math::log10) },
    "sqrt"        to FunctionSignature(true, listOf(BuiltinFunctionParam("value", listOf(DataType.FLOAT))), DataType.FLOAT) { a, p, n, h -> oneDoubleArg(a, p, n, h, Math::sqrt) },
    "rad"         to FunctionSignature(true, listOf(BuiltinFunctionParam("value", listOf(DataType.FLOAT))), DataType.FLOAT) { a, p, n, h -> oneDoubleArg(a, p, n, h, Math::toRadians) },
    "deg"         to FunctionSignature(true, listOf(BuiltinFunctionParam("value", listOf(DataType.FLOAT))), DataType.FLOAT) { a, p, n, h -> oneDoubleArg(a, p, n, h, Math::toDegrees) },
    "avg"         to FunctionSignature(true, listOf(BuiltinFunctionParam("values", listOf(DataType.ARRAY, DataType.ARRAY_W, DataType.MATRIX))), DataType.FLOAT, ::builtinAvg),
    "abs"         to FunctionSignature(true, listOf(BuiltinFunctionParam("value", listOf(DataType.FLOAT))), DataType.FLOAT, ::builtinAbs),
    "round"       to FunctionSignature(true, listOf(BuiltinFunctionParam("value", listOf(DataType.FLOAT))), null) { a, p, n, h -> oneDoubleArgOutputInt(a, p, n, h, Math::round) },   // type depends on arg
    "floor"       to FunctionSignature(true, listOf(BuiltinFunctionParam("value", listOf(DataType.FLOAT))), null) { a, p, n, h -> oneDoubleArgOutputInt(a, p, n, h, Math::floor) },   // type depends on arg
    "ceil"        to FunctionSignature(true, listOf(BuiltinFunctionParam("value", listOf(DataType.FLOAT))), null) { a, p, n, h -> oneDoubleArgOutputInt(a, p, n, h, Math::ceil) },    // type depends on arg
    "max"         to FunctionSignature(true, listOf(BuiltinFunctionParam("values", listOf(DataType.ARRAY, DataType.ARRAY_W, DataType.MATRIX))), null) { a, p, n, h -> collectionArgOutputNumber(a, p, n, h) { it.max()!! }},        // type depends on args
    "min"         to FunctionSignature(true, listOf(BuiltinFunctionParam("values", listOf(DataType.ARRAY, DataType.ARRAY_W, DataType.MATRIX))), null) { a, p, n, h -> collectionArgOutputNumber(a, p, n, h) { it.min()!! }},        // type depends on args
    "sum"         to FunctionSignature(true, listOf(BuiltinFunctionParam("values", listOf(DataType.ARRAY, DataType.ARRAY_W, DataType.MATRIX))), null) { a, p, n, h -> collectionArgOutputNumber(a, p, n, h) { it.sum() }},        // type depends on args
    "len"         to FunctionSignature(true, listOf(BuiltinFunctionParam("values", listOf(DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS, DataType.ARRAY, DataType.ARRAY_W, DataType.MATRIX))), null, ::builtinLen),        // type depends on args
    "any"         to FunctionSignature(true, listOf(BuiltinFunctionParam("values", listOf(DataType.ARRAY, DataType.ARRAY_W, DataType.MATRIX))), DataType.BYTE) { a, p, n, h -> collectionArgOutputBoolean(a, p, n, h) { it.any { v -> v != 0.0} }},
    "all"         to FunctionSignature(true, listOf(BuiltinFunctionParam("values", listOf(DataType.ARRAY, DataType.ARRAY_W, DataType.MATRIX))), DataType.BYTE) { a, p, n, h -> collectionArgOutputBoolean(a, p, n, h) { it.all { v -> v != 0.0} }},
    "lsb"         to FunctionSignature(true, listOf(BuiltinFunctionParam("value", listOf(DataType.WORD))), DataType.BYTE) { a, p, n, h -> oneIntArgOutputInt(a, p, n, h) { x: Int -> x and 255 }},
    "msb"         to FunctionSignature(true, listOf(BuiltinFunctionParam("value", listOf(DataType.WORD))), DataType.BYTE) { a, p, n, h -> oneIntArgOutputInt(a, p, n, h) { x: Int -> x ushr 8 and 255}},
    "flt"         to FunctionSignature(true, listOf(BuiltinFunctionParam("value", listOf(DataType.BYTE, DataType.WORD))), DataType.FLOAT, ::builtinFlt),
    "rnd"         to FunctionSignature(true, emptyList(), DataType.BYTE),
    "rndw"        to FunctionSignature(true, emptyList(), DataType.WORD),
    "rndf"        to FunctionSignature(true, emptyList(), DataType.FLOAT),
    "set_carry"   to FunctionSignature(false, emptyList(), null),
    "clear_carry" to FunctionSignature(false, emptyList(), null),
    "set_irqd"    to FunctionSignature(false, emptyList(), null),
    "clear_irqd"  to FunctionSignature(false, emptyList(), null),
    "str2byte"    to FunctionSignature(true, listOf(BuiltinFunctionParam("string", listOf(DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS))), DataType.BYTE),
    "str2word"    to FunctionSignature(true, listOf(BuiltinFunctionParam("string", listOf(DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS))), DataType.WORD),
    "str2float"   to FunctionSignature(true, listOf(BuiltinFunctionParam("string", listOf(DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS))), DataType.FLOAT),
    "_vm_write_memchr"  to FunctionSignature(false, listOf(BuiltinFunctionParam("address", listOf(DataType.WORD))), null),
    "_vm_write_memstr"  to FunctionSignature(false, listOf(BuiltinFunctionParam("address", listOf(DataType.WORD))), null),
    "_vm_write_num"     to FunctionSignature(false, listOf(BuiltinFunctionParam("number", listOf(DataType.BYTE, DataType.WORD, DataType.FLOAT))), null),
    "_vm_write_char"    to FunctionSignature(false, listOf(BuiltinFunctionParam("char", listOf(DataType.BYTE))), null),
    "_vm_write_str"     to FunctionSignature(false, listOf(BuiltinFunctionParam("string", listOf(DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS))), null),
    "_vm_input_str"     to FunctionSignature(false, listOf(BuiltinFunctionParam("intovar", listOf(DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS))), null),
    "_vm_gfx_clearscr"  to FunctionSignature(false, listOf(BuiltinFunctionParam("color", listOf(DataType.BYTE))), null),
    "_vm_gfx_pixel"     to FunctionSignature(false, listOf(
                                                        BuiltinFunctionParam("x", listOf(DataType.BYTE, DataType.WORD)),
                                                        BuiltinFunctionParam("y", listOf(DataType.BYTE, DataType.WORD)),
                                                        BuiltinFunctionParam("color", listOf(DataType.BYTE))), null),
    "_vm_gfx_text"      to FunctionSignature(false, listOf(
                                                        BuiltinFunctionParam("x", listOf(DataType.BYTE, DataType.WORD)),
                                                        BuiltinFunctionParam("y", listOf(DataType.BYTE, DataType.WORD)),
                                                        BuiltinFunctionParam("color", listOf(DataType.BYTE)),
                                                        BuiltinFunctionParam("text", listOf(DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS))),
                                                        null)
)


fun builtinFunctionReturnType(function: String, args: List<IExpression>, namespace: INameScope, heap: HeapValues): DataType? {

    fun datatypeFromListArg(arglist: IExpression): DataType {
        if(arglist is LiteralValue) {
            if(arglist.type==DataType.ARRAY || arglist.type==DataType.ARRAY_W || arglist.type==DataType.MATRIX) {
                val dt = arglist.arrayvalue!!.map {it.resultingDatatype(namespace, heap)}
                if(dt.any { it!=DataType.BYTE && it!=DataType.WORD && it!=DataType.FLOAT}) {
                    throw FatalAstException("fuction $function only accepts array of numeric values")
                }
                if(dt.any { it==DataType.FLOAT }) return DataType.FLOAT
                if(dt.any { it==DataType.WORD }) return DataType.WORD
                return DataType.BYTE
            }
        }
        if(arglist is IdentifierReference) {
            val dt = arglist.resultingDatatype(namespace, heap)
            return when(dt) {
                DataType.BYTE, DataType.WORD, DataType.FLOAT,
                DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> dt
                DataType.ARRAY -> DataType.BYTE
                DataType.ARRAY_W -> DataType.WORD
                DataType.MATRIX -> DataType.BYTE
                null -> throw FatalAstException("function requires one argument which is an array $function")
            }
        }
        throw FatalAstException("function requires one argument which is an array $function")
    }

    val func = BuiltinFunctions[function]!!
    if(func.returntype!=null)
        return func.returntype
    // function has return values, but the return type depends on the arguments

    return when (function) {
        "max", "min" -> {
            val dt = datatypeFromListArg(args.single())
            when(dt) {
                DataType.BYTE, DataType.WORD, DataType.FLOAT -> dt
                DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> DataType.BYTE
                DataType.ARRAY -> DataType.BYTE
                DataType.ARRAY_W -> DataType.WORD
                DataType.MATRIX -> DataType.BYTE
            }
        }
        "round", "floor", "ceil" -> {
            val dt=args.single().resultingDatatype(namespace, heap)
            when(dt) {
                DataType.BYTE -> DataType.BYTE
                DataType.WORD -> DataType.WORD
                DataType.FLOAT -> DataType.WORD
                else -> null
            }
        }
        "sum" -> {
            val dt=datatypeFromListArg(args.single())
            when(dt) {
                DataType.BYTE, DataType.WORD -> DataType.WORD
                DataType.FLOAT -> DataType.FLOAT
                DataType.ARRAY, DataType.ARRAY_W -> DataType.WORD
                DataType.MATRIX -> DataType.WORD
                DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> DataType.WORD
            }
        }
        "len" -> {
            // len of a str is always 0..255 so always a byte,
            // len of other things is assumed to need a word (even though the actual length could be less than 256)
            val arg = args.single()
            when(arg) {
                is IdentifierReference -> {
                    val stmt = arg.targetStatement(namespace)
                    when(stmt) {
                        is VarDecl -> {
                            val value = stmt.value
                            if(value is LiteralValue) {
                                if(value.isString) return DataType.BYTE        // strings are 0..255
                            }
                        }
                    }
                    DataType.WORD   // assume other lengths are words for now.
                }
                is LiteralValue -> throw FatalAstException("len of literalvalue should have been const-folded away already")
                else -> DataType.WORD
            }
        }
        else -> throw FatalAstException("unknown result type for builtin function $function")
    }
}


class NotConstArgumentException: AstException("not a const argument to a built-in function")


private fun oneDoubleArg(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues, function: (arg: Double)->Number): LiteralValue {
    if(args.size!=1)
        throw SyntaxError("built-in function requires one floating point argument", position)
    val constval = args[0].constValue(namespace, heap) ?: throw NotConstArgumentException()
    if(constval.type!=DataType.FLOAT)
        throw SyntaxError("built-in function requires one floating point argument", position)

    val float = constval.asNumericValue?.toDouble()!!
    return numericLiteral(function(float), args[0].position)
}

private fun oneDoubleArgOutputInt(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues, function: (arg: Double)->Number): LiteralValue {
    if(args.size!=1)
        throw SyntaxError("built-in function requires one floating point argument", position)
    val constval = args[0].constValue(namespace, heap) ?: throw NotConstArgumentException()
    val float: Double = when(constval.type) {
        DataType.BYTE, DataType.WORD, DataType.FLOAT -> constval.asNumericValue!!.toDouble()
        else -> throw SyntaxError("built-in function requires one floating point argument", position)
    }
    return numericLiteral(function(float).toInt(), args[0].position)
}

private fun oneIntArgOutputInt(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues, function: (arg: Int)->Number): LiteralValue {
    if(args.size!=1)
        throw SyntaxError("built-in function requires one integer argument", position)
    val constval = args[0].constValue(namespace, heap) ?: throw NotConstArgumentException()
    if(constval.type!=DataType.BYTE && constval.type!=DataType.WORD)
        throw SyntaxError("built-in function requires one integer argument", position)

    val integer = constval.asNumericValue?.toInt()!!
    return numericLiteral(function(integer).toInt(), args[0].position)
}

private fun collectionArgOutputNumber(args: List<IExpression>, position: Position,
                                      namespace:INameScope, heap: HeapValues,
                                      function: (arg: Collection<Double>)->Number): LiteralValue {
    if(args.size!=1)
        throw SyntaxError("builtin function requires one non-scalar argument", position)
    val iterable = args[0].constValue(namespace, heap) ?: throw NotConstArgumentException()

    val result = if(iterable.arrayvalue != null) {
        val constants = iterable.arrayvalue.map { it.constValue(namespace, heap)?.asNumericValue }
        if(null in constants)
            throw NotConstArgumentException()
        function(constants.map { it!!.toDouble() }).toDouble()
    } else {
        when(iterable.type) {
            DataType.BYTE, DataType.WORD, DataType.FLOAT -> throw SyntaxError("function expects an iterable type", position)
            else -> {
                if(iterable.heapId==null)
                    throw FatalAstException("iterable value should be on the heap")
                val array = heap.get(iterable.heapId).array ?: throw SyntaxError("function expects an iterable type", position)
                function(array.map { it.toDouble() })
            }
        }
    }
    return numericLiteral(result, args[0].position)
}

private fun collectionArgOutputBoolean(args: List<IExpression>, position: Position,
                                       namespace:INameScope, heap: HeapValues,
                                       function: (arg: Collection<Double>)->Boolean): LiteralValue {
    if(args.size!=1)
        throw SyntaxError("builtin function requires one non-scalar argument", position)
    val iterable = args[0].constValue(namespace, heap) ?: throw NotConstArgumentException()

    val result = if(iterable.arrayvalue != null) {
        val constants = iterable.arrayvalue.map { it.constValue(namespace, heap)?.asNumericValue }
        if(null in constants)
            throw NotConstArgumentException()
        function(constants.map { it!!.toDouble() })
    } else {
        val array = heap.get(iterable.heapId!!).array ?: throw SyntaxError("function requires array/matrix argument", position)
        function(array.map { it.toDouble() })
    }
    return LiteralValue.fromBoolean(result, position)
}

private fun builtinFlt(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues): LiteralValue {
    // 1 numeric arg, convert to float
    if(args.size!=1)
        throw SyntaxError("flt requires one numeric argument", position)

    val constval = args[0].constValue(namespace, heap) ?: throw NotConstArgumentException()
    val number = constval.asNumericValue ?: throw SyntaxError("flt requires one numeric argument", position)
    return LiteralValue(DataType.FLOAT, floatvalue = number.toDouble(), position = position)
}

private fun builtinAbs(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues): LiteralValue {
    // 1 arg, type = float or int, result type= same as argument type
    if(args.size!=1)
        throw SyntaxError("abs requires one numeric argument", position)

    val constval = args[0].constValue(namespace, heap) ?: throw NotConstArgumentException()
    val number = constval.asNumericValue
    return when (number) {
        is Int, is Byte, is Short -> numericLiteral(Math.abs(number.toInt()), args[0].position)
        is Double -> numericLiteral(Math.abs(number.toDouble()), args[0].position)
        else -> throw SyntaxError("abs requires one numeric argument", position)
    }
}

private fun builtinAvg(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues): LiteralValue {
    if(args.size!=1)
        throw SyntaxError("avg requires array/matrix argument", position)
    val iterable = args[0].constValue(namespace, heap) ?: throw NotConstArgumentException()

    val result = if(iterable.arrayvalue!=null) {
        val constants = iterable.arrayvalue.map { it.constValue(namespace, heap)?.asNumericValue }
        if (null in constants)
            throw NotConstArgumentException()
        (constants.map { it!!.toDouble() }).average()
    }
    else {
        val array = heap.get(iterable.heapId!!).array ?: throw SyntaxError("avg requires array/matrix argument", position)
        array.average()
    }
    return numericLiteral(result, args[0].position)
}

private fun builtinLen(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues): LiteralValue {
    if(args.size!=1)
        throw SyntaxError("len requires one argument", position)
    var argument = args[0].constValue(namespace, heap)
    if(argument==null) {
        if(args[0] !is IdentifierReference)
            throw SyntaxError("len over weird argument ${args[0]}", position)
        argument = ((args[0] as IdentifierReference).targetStatement(namespace) as? VarDecl)?.value?.constValue(namespace, heap)
                ?: throw SyntaxError("len over weird argument ${args[0]}", position)
    }
    return when(argument.type) {
        DataType.ARRAY, DataType.ARRAY_W -> {
            val arraySize = argument.arrayvalue?.size ?: heap.get(argument.heapId!!).array!!.size
            numericLiteral(arraySize, args[0].position)
        }
        DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> {
            val str = argument.strvalue ?: heap.get(argument.heapId!!).str!!
            numericLiteral(str.length, args[0].position)
        }
        else -> throw SyntaxError("len of weird argument ${args[0]}", position)
    }
}

private fun numericLiteral(value: Number, position: Position): LiteralValue {
    val floatNum=value.toDouble()
    val tweakedValue: Number =
            if(floatNum==Math.floor(floatNum) && floatNum in -32768..65535)
                floatNum.toInt()  // we have an integer disguised as a float.
            else
                floatNum

    return when(tweakedValue) {
        is Int -> LiteralValue.optimalNumeric(value.toInt(), position)
        is Short -> LiteralValue.optimalNumeric(value.toInt(), position)
        is Byte -> LiteralValue(DataType.BYTE, bytevalue = value.toShort(), position = position)
        is Double -> LiteralValue(DataType.FLOAT, floatvalue = value.toDouble(), position = position)
        is Float -> LiteralValue(DataType.FLOAT, floatvalue = value.toDouble(), position = position)
        else -> throw FatalAstException("invalid number type ${value::class}")
    }
}
