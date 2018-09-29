package prog8.functions

import prog8.ast.*
import prog8.compiler.HeapValues
import kotlin.math.log2


val BuiltinFunctionNames = setOf(
        "set_carry", "clear_carry", "set_irqd", "clear_irqd", "rol", "ror", "rol2", "ror2", "lsl", "lsr",
        "sin", "cos", "abs", "acos", "asin", "tan", "atan", "rnd", "rndw", "rndf",
        "ln", "log2", "log10", "sqrt", "rad", "deg", "round", "floor", "ceil",
        "max", "min", "avg", "sum", "len", "any", "all", "lsb", "msb", "flt",
        "_vm_write_memchr", "_vm_write_memstr", "_vm_write_num", "_vm_write_char",
        "_vm_write_str", "_vm_input_str", "_vm_gfx_clearscr", "_vm_gfx_pixel", "_vm_gfx_text"
        )

val BuiltinFunctionsWithoutSideEffects = BuiltinFunctionNames - setOf(
        "set_carry", "clear_carry", "set_irqd", "clear_irqd", "lsl", "lsr", "rol", "ror", "rol2", "ror2",
        "_vm_write_memchr", "_vm_write_memstr", "_vm_write_num", "_vm_write_char",
        "_vm_write_str", "_vm_gfx_clearscr", "_vm_gfx_pixel", "_vm_gfx_text")


fun builtinFunctionReturnType(function: String, args: List<IExpression>, namespace: INameScope, heap: HeapValues): DataType? {
    fun integerDatatypeFromArg(arg: IExpression): DataType {
        val dt = arg.resultingDatatype(namespace, heap)
        return when(dt) {
            DataType.BYTE -> DataType.BYTE
            DataType.WORD -> DataType.WORD
            DataType.FLOAT -> DataType.WORD
            else -> throw FatalAstException("fuction $function can only return a numeric value")
        }
    }

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

    return when (function) {
        "sin", "cos", "tan", "asin", "acos", "atan", "ln", "log2", "log10",
            "sqrt", "rad", "deg", "avg", "rndf", "flt" -> DataType.FLOAT
        "lsb", "msb", "any", "all", "rnd" -> DataType.BYTE
        "rndw" -> DataType.WORD
        "rol", "rol2", "ror", "ror2", "lsl", "lsr", "set_carry", "clear_carry", "set_irqd", "clear_irqd" -> null // no return value so no datatype
        "abs" -> args.single().resultingDatatype(namespace, heap)
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
        "round", "floor", "ceil" -> integerDatatypeFromArg(args.single())
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
        "_vm_write_memchr", "_vm_write_memstr", "_vm_write_num", "_vm_write_char",
        "_vm_write_str", "_vm_gfx_clearscr", "_vm_gfx_pixel", "_vm_gfx_text" -> null  // no return value for these
        "_vm_input_str" -> DataType.STR
        else -> throw FatalAstException("invalid builtin function $function")
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
    var iterable = args[0].constValue(namespace, heap) ?: throw NotConstArgumentException()

    val result = if(iterable.arrayvalue != null) {
        val constants = iterable.arrayvalue!!.map { it.constValue(namespace, heap)?.asNumericValue }
        if(constants.contains(null))
            throw NotConstArgumentException()
        function(constants.map { it!!.toDouble() }).toDouble()
    } else {
        val array = heap.get(iterable.heapId!!).array ?: throw SyntaxError("function requires array/matrix argument", position)
        function(array.map { it.toDouble() })
    }
    return numericLiteral(result, args[0].position)
}

private fun collectionArgOutputBoolean(args: List<IExpression>, position: Position,
                                       namespace:INameScope, heap: HeapValues,
                                       function: (arg: Collection<Double>)->Boolean): LiteralValue {
    if(args.size!=1)
        throw SyntaxError("builtin function requires one non-scalar argument", position)
    var iterable = args[0].constValue(namespace, heap) ?: throw NotConstArgumentException()

    val result = if(iterable.arrayvalue != null) {
        val constants = iterable.arrayvalue!!.map { it.constValue(namespace, heap)?.asNumericValue }
        if(constants.contains(null))
            throw NotConstArgumentException()
        function(constants.map { it!!.toDouble() })
    } else {
        val array = heap.get(iterable.heapId!!).array ?: throw SyntaxError("function requires array/matrix argument", position)
        function(array.map { it.toDouble() })
    }
    return LiteralValue.fromBoolean(result, position)
}

fun builtinRound(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues): LiteralValue
        = oneDoubleArgOutputInt(args, position, namespace, heap, Math::round)

fun builtinFloor(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues): LiteralValue
        = oneDoubleArgOutputInt(args, position, namespace, heap, Math::floor)

fun builtinCeil(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues): LiteralValue
        = oneDoubleArgOutputInt(args, position, namespace, heap, Math::ceil)

fun builtinSin(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues): LiteralValue
        = oneDoubleArg(args, position, namespace, heap, Math::sin)

fun builtinCos(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues): LiteralValue
        = oneDoubleArg(args, position, namespace, heap, Math::cos)

fun builtinAcos(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues): LiteralValue
        = oneDoubleArg(args, position, namespace, heap, Math::acos)

fun builtinAsin(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues): LiteralValue
        = oneDoubleArg(args, position, namespace, heap, Math::asin)

fun builtinTan(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues): LiteralValue
        = oneDoubleArg(args, position, namespace, heap, Math::tan)

fun builtinAtan(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues): LiteralValue
        = oneDoubleArg(args, position, namespace, heap, Math::atan)

fun builtinLn(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues): LiteralValue
        = oneDoubleArg(args, position, namespace, heap, Math::log)

fun builtinLog2(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues): LiteralValue
        = oneDoubleArg(args, position, namespace, heap, ::log2)

fun builtinLog10(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues): LiteralValue
        = oneDoubleArg(args, position, namespace, heap, Math::log10)

fun builtinSqrt(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues): LiteralValue
        = oneDoubleArg(args, position, namespace, heap, Math::sqrt)

fun builtinRad(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues): LiteralValue
        = oneDoubleArg(args, position, namespace, heap, Math::toRadians)

fun builtinDeg(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues): LiteralValue
        = oneDoubleArg(args, position, namespace, heap, Math::toDegrees)

fun builtinFlt(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues): LiteralValue {
    // 1 numeric arg, convert to float
    if(args.size!=1)
        throw SyntaxError("flt requires one numeric argument", position)

    val constval = args[0].constValue(namespace, heap) ?: throw NotConstArgumentException()
    val number = constval.asNumericValue ?: throw SyntaxError("flt requires one numeric argument", position)
    return LiteralValue(DataType.FLOAT, floatvalue = number.toDouble(), position = position)
}

fun builtinAbs(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues): LiteralValue {
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


fun builtinLsb(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues): LiteralValue
        = oneIntArgOutputInt(args, position, namespace, heap) { x: Int -> x and 255 }

fun builtinMsb(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues): LiteralValue
        = oneIntArgOutputInt(args, position, namespace, heap) { x: Int -> x ushr 8 and 255}

fun builtinMin(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues): LiteralValue
        = collectionArgOutputNumber(args, position, namespace, heap) { it.min()!! }

fun builtinMax(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues): LiteralValue
        = collectionArgOutputNumber(args, position, namespace, heap) { it.max()!! }

fun builtinSum(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues): LiteralValue
        = collectionArgOutputNumber(args, position, namespace, heap) { it.sum() }

fun builtinAvg(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues): LiteralValue {
    if(args.size!=1)
        throw SyntaxError("avg requires array/matrix argument", position)
    var iterable = args[0].constValue(namespace, heap) ?: throw NotConstArgumentException()

    val result = if(iterable.arrayvalue!=null) {
        val constants = iterable.arrayvalue!!.map { it.constValue(namespace, heap)?.asNumericValue }
        if (constants.contains(null))
            throw NotConstArgumentException()
        (constants.map { it!!.toDouble() }).average()
    }
    else {
        val array = heap.get(iterable.heapId!!).array ?: throw SyntaxError("avg requires array/matrix argument", position)
        array.average()
    }
    return numericLiteral(result, args[0].position)
}

fun builtinLen(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues): LiteralValue {
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

fun builtinAny(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues): LiteralValue
        = collectionArgOutputBoolean(args, position, namespace, heap) { it.any { v -> v != 0.0} }

fun builtinAll(args: List<IExpression>, position: Position, namespace:INameScope, heap: HeapValues): LiteralValue
        = collectionArgOutputBoolean(args, position, namespace, heap) { it.all { v -> v != 0.0} }


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
