package prog8.functions

import prog8.ast.*
import prog8.compiler.CompilerException
import kotlin.math.*


class BuiltinFunctionParam(val name: String, val possibleDatatypes: Set<DataType>)

class FunctionSignature(val pure: Boolean,      // does it have side effects?
                        val parameters: List<BuiltinFunctionParam>,
                        val returntype: DataType?,
                        val constExpressionFunc: ((args: List<IExpression>, position: Position, program: Program) -> LiteralValue)? = null)


val BuiltinFunctions = mapOf(
        // this set of function have no return value and operate in-place:
    "rol"         to FunctionSignature(false, listOf(BuiltinFunctionParam("item", setOf(DataType.UBYTE, DataType.UWORD))), null),
    "ror"         to FunctionSignature(false, listOf(BuiltinFunctionParam("item", setOf(DataType.UBYTE, DataType.UWORD))), null),
    "rol2"        to FunctionSignature(false, listOf(BuiltinFunctionParam("item", setOf(DataType.UBYTE, DataType.UWORD))), null),
    "ror2"        to FunctionSignature(false, listOf(BuiltinFunctionParam("item", setOf(DataType.UBYTE, DataType.UWORD))), null),
    "lsl"         to FunctionSignature(false, listOf(BuiltinFunctionParam("item", IntegerDatatypes)), null),
    "lsr"         to FunctionSignature(false, listOf(BuiltinFunctionParam("item", IntegerDatatypes)), null),
        // these few have a return value depending on the argument(s):
    "max"         to FunctionSignature(true, listOf(BuiltinFunctionParam("values", ArrayDatatypes)), null) { a, p, prg -> collectionArgOutputNumber(a, p, prg) { it.max()!! }},    // type depends on args
    "min"         to FunctionSignature(true, listOf(BuiltinFunctionParam("values", ArrayDatatypes)), null) { a, p, prg -> collectionArgOutputNumber(a, p, prg) { it.min()!! }},    // type depends on args
    "sum"         to FunctionSignature(true, listOf(BuiltinFunctionParam("values", ArrayDatatypes)), null) { a, p, prg -> collectionArgOutputNumber(a, p, prg) { it.sum() }},      // type depends on args
    "abs"         to FunctionSignature(true, listOf(BuiltinFunctionParam("value", NumericDatatypes)), null, ::builtinAbs),      // type depends on argument
    "len"         to FunctionSignature(true, listOf(BuiltinFunctionParam("values", IterableDatatypes)), null, ::builtinLen),    // type is UBYTE or UWORD depending on actual length
        // normal functions follow:
    "sin"         to FunctionSignature(true, listOf(BuiltinFunctionParam("rads", setOf(DataType.FLOAT))), DataType.FLOAT) { a, p, prg -> oneDoubleArg(a, p, prg, Math::sin) },
    "sin8"        to FunctionSignature(true, listOf(BuiltinFunctionParam("angle8", setOf(DataType.UBYTE))), DataType.BYTE, ::builtinSin8 ),
    "sin8u"       to FunctionSignature(true, listOf(BuiltinFunctionParam("angle8", setOf(DataType.UBYTE))), DataType.UBYTE, ::builtinSin8u ),
    "sin16"       to FunctionSignature(true, listOf(BuiltinFunctionParam("angle8", setOf(DataType.UBYTE))), DataType.WORD, ::builtinSin16 ),
    "sin16u"      to FunctionSignature(true, listOf(BuiltinFunctionParam("angle8", setOf(DataType.UBYTE))), DataType.UWORD, ::builtinSin16u ),
    "cos"         to FunctionSignature(true, listOf(BuiltinFunctionParam("rads", setOf(DataType.FLOAT))), DataType.FLOAT) { a, p, prg -> oneDoubleArg(a, p, prg, Math::cos) },
    "cos8"        to FunctionSignature(true, listOf(BuiltinFunctionParam("angle8", setOf(DataType.UBYTE))), DataType.BYTE, ::builtinCos8 ),
    "cos8u"       to FunctionSignature(true, listOf(BuiltinFunctionParam("angle8", setOf(DataType.UBYTE))), DataType.UBYTE, ::builtinCos8u ),
    "cos16"       to FunctionSignature(true, listOf(BuiltinFunctionParam("angle8", setOf(DataType.UBYTE))), DataType.WORD, ::builtinCos16 ),
    "cos16u"      to FunctionSignature(true, listOf(BuiltinFunctionParam("angle8", setOf(DataType.UBYTE))), DataType.UWORD, ::builtinCos16u ),
    "tan"         to FunctionSignature(true, listOf(BuiltinFunctionParam("rads", setOf(DataType.FLOAT))), DataType.FLOAT) { a, p, prg -> oneDoubleArg(a, p, prg, Math::tan) },
    "atan"        to FunctionSignature(true, listOf(BuiltinFunctionParam("rads", setOf(DataType.FLOAT))), DataType.FLOAT) { a, p, prg -> oneDoubleArg(a, p, prg, Math::atan) },
    "ln"          to FunctionSignature(true, listOf(BuiltinFunctionParam("value", setOf(DataType.FLOAT))), DataType.FLOAT) { a, p, prg -> oneDoubleArg(a, p, prg, Math::log) },
    "log2"        to FunctionSignature(true, listOf(BuiltinFunctionParam("value", setOf(DataType.FLOAT))), DataType.FLOAT) { a, p, prg -> oneDoubleArg(a, p, prg, ::log2) },
    "sqrt16"      to FunctionSignature(true, listOf(BuiltinFunctionParam("value", setOf(DataType.UWORD))), DataType.UBYTE) { a, p, prg -> oneIntArgOutputInt(a, p, prg) { sqrt(it.toDouble()).toInt() } },
    "sqrt"        to FunctionSignature(true, listOf(BuiltinFunctionParam("value", setOf(DataType.FLOAT))), DataType.FLOAT) { a, p, prg -> oneDoubleArg(a, p, prg, Math::sqrt) },
    "rad"         to FunctionSignature(true, listOf(BuiltinFunctionParam("value", setOf(DataType.FLOAT))), DataType.FLOAT) { a, p, prg -> oneDoubleArg(a, p, prg, Math::toRadians) },
    "deg"         to FunctionSignature(true, listOf(BuiltinFunctionParam("value", setOf(DataType.FLOAT))), DataType.FLOAT) { a, p, prg -> oneDoubleArg(a, p, prg, Math::toDegrees) },
    "avg"         to FunctionSignature(true, listOf(BuiltinFunctionParam("values", ArrayDatatypes)), DataType.FLOAT, ::builtinAvg),
    "round"       to FunctionSignature(true, listOf(BuiltinFunctionParam("value", setOf(DataType.FLOAT))), DataType.FLOAT) { a, p, prg -> oneDoubleArgOutputWord(a, p, prg, Math::round) },
    "floor"       to FunctionSignature(true, listOf(BuiltinFunctionParam("value", setOf(DataType.FLOAT))), DataType.FLOAT) { a, p, prg -> oneDoubleArgOutputWord(a, p, prg, Math::floor) },
    "ceil"        to FunctionSignature(true, listOf(BuiltinFunctionParam("value", setOf(DataType.FLOAT))), DataType.FLOAT) { a, p, prg -> oneDoubleArgOutputWord(a, p, prg, Math::ceil) },
    "any"         to FunctionSignature(true, listOf(BuiltinFunctionParam("values", ArrayDatatypes)), DataType.UBYTE) { a, p, prg -> collectionArgOutputBoolean(a, p, prg) { it.any { v -> v != 0.0} }},
    "all"         to FunctionSignature(true, listOf(BuiltinFunctionParam("values", ArrayDatatypes)), DataType.UBYTE) { a, p, prg -> collectionArgOutputBoolean(a, p, prg) { it.all { v -> v != 0.0} }},
    "lsb"         to FunctionSignature(true, listOf(BuiltinFunctionParam("value", setOf(DataType.UWORD, DataType.WORD))), DataType.UBYTE) { a, p, prg -> oneIntArgOutputInt(a, p, prg) { x: Int -> x and 255 }},
    "msb"         to FunctionSignature(true, listOf(BuiltinFunctionParam("value", setOf(DataType.UWORD, DataType.WORD))), DataType.UBYTE) { a, p, prg -> oneIntArgOutputInt(a, p, prg) { x: Int -> x ushr 8 and 255}},
    "mkword"      to FunctionSignature(true, listOf(
                                                        BuiltinFunctionParam("lsb", setOf(DataType.UBYTE)),
                                                        BuiltinFunctionParam("msb", setOf(DataType.UBYTE))), DataType.UWORD, ::builtinMkword),
    "rnd"         to FunctionSignature(true, emptyList(), DataType.UBYTE),
    "rndw"        to FunctionSignature(true, emptyList(), DataType.UWORD),
    "rndf"        to FunctionSignature(true, emptyList(), DataType.FLOAT),
    "rsave"       to FunctionSignature(false, emptyList(), null),
    "rrestore"    to FunctionSignature(false, emptyList(), null),
    "set_carry"   to FunctionSignature(false, emptyList(), null),
    "clear_carry" to FunctionSignature(false, emptyList(), null),
    "set_irqd"    to FunctionSignature(false, emptyList(), null),
    "clear_irqd"  to FunctionSignature(false, emptyList(), null),
    "read_flags"  to FunctionSignature(false, emptyList(), DataType.UBYTE),
    "swap"        to FunctionSignature(false, listOf(BuiltinFunctionParam("first", NumericDatatypes), BuiltinFunctionParam("second", NumericDatatypes)), null),
    "memcopy"     to FunctionSignature(false, listOf(
                                                        BuiltinFunctionParam("from", IterableDatatypes + setOf(DataType.UWORD)),
                                                        BuiltinFunctionParam("to", IterableDatatypes + setOf(DataType.UWORD)),
                                                        BuiltinFunctionParam("numbytes", setOf(DataType.UBYTE))), null),
    "memset"      to FunctionSignature(false, listOf(
                                                        BuiltinFunctionParam("address", IterableDatatypes + setOf(DataType.UWORD)),
                                                        BuiltinFunctionParam("numbytes", setOf(DataType.UWORD)),
                                                        BuiltinFunctionParam("bytevalue", ByteDatatypes)), null),
    "memsetw"     to FunctionSignature(false, listOf(
                                                        BuiltinFunctionParam("address", IterableDatatypes + setOf(DataType.UWORD)),
                                                        BuiltinFunctionParam("numwords", setOf(DataType.UWORD)),
                                                        BuiltinFunctionParam("wordvalue", setOf(DataType.UWORD, DataType.WORD))), null),
    "strlen"      to FunctionSignature(true, listOf(BuiltinFunctionParam("string", StringDatatypes)), DataType.UBYTE, ::builtinStrlen),
    "vm_write_memchr"  to FunctionSignature(false, listOf(BuiltinFunctionParam("address", setOf(DataType.UWORD))), null),
    "vm_write_memstr"  to FunctionSignature(false, listOf(BuiltinFunctionParam("address", setOf(DataType.UWORD))), null),
    "vm_write_num"     to FunctionSignature(false, listOf(BuiltinFunctionParam("number", NumericDatatypes)), null),
    "vm_write_char"    to FunctionSignature(false, listOf(BuiltinFunctionParam("char", setOf(DataType.UBYTE))), null),
    "vm_write_str"     to FunctionSignature(false, listOf(BuiltinFunctionParam("string", StringDatatypes)), null),
    "vm_input_str"     to FunctionSignature(false, listOf(BuiltinFunctionParam("intovar", StringDatatypes)), null),
    "vm_gfx_clearscr"  to FunctionSignature(false, listOf(BuiltinFunctionParam("color", setOf(DataType.UBYTE))), null),
    "vm_gfx_pixel"     to FunctionSignature(false, listOf(
                                                        BuiltinFunctionParam("x", IntegerDatatypes),
                                                        BuiltinFunctionParam("y", IntegerDatatypes),
                                                        BuiltinFunctionParam("color", IntegerDatatypes)), null),
    "vm_gfx_line"     to FunctionSignature(false, listOf(
                                                        BuiltinFunctionParam("x1", IntegerDatatypes),
                                                        BuiltinFunctionParam("y1", IntegerDatatypes),
                                                        BuiltinFunctionParam("x2", IntegerDatatypes),
                                                        BuiltinFunctionParam("y2", IntegerDatatypes),
                                                        BuiltinFunctionParam("color", IntegerDatatypes)), null),
    "vm_gfx_text"      to FunctionSignature(false, listOf(
                                                        BuiltinFunctionParam("x", IntegerDatatypes),
                                                        BuiltinFunctionParam("y", IntegerDatatypes),
                                                        BuiltinFunctionParam("color", IntegerDatatypes),
                                                        BuiltinFunctionParam("text", StringDatatypes)),
                                                        null)
)


fun builtinFunctionReturnType(function: String, args: List<IExpression>, program: Program): DataType? {

    fun datatypeFromIterableArg(arglist: IExpression): DataType {
        if(arglist is LiteralValue) {
            if(arglist.type==DataType.ARRAY_UB || arglist.type==DataType.ARRAY_UW || arglist.type==DataType.ARRAY_F) {
                val dt = arglist.arrayvalue!!.map {it.inferType(program)}
                if(dt.any { it!=DataType.UBYTE && it!=DataType.UWORD && it!=DataType.FLOAT}) {
                    throw FatalAstException("fuction $function only accepts arraysize of numeric values")
                }
                if(dt.any { it==DataType.FLOAT }) return DataType.FLOAT
                if(dt.any { it==DataType.UWORD }) return DataType.UWORD
                return DataType.UBYTE
            }
        }
        if(arglist is IdentifierReference) {
            val dt = arglist.inferType(program)
            return when(dt) {
                in NumericDatatypes -> dt!!
                in StringDatatypes -> dt!!
                in ArrayDatatypes -> ArrayElementTypes.getValue(dt!!)
                else -> throw FatalAstException("function '$function' requires one argument which is an iterable")
            }
        }
        throw FatalAstException("function '$function' requires one argument which is an iterable")
    }

    val func = BuiltinFunctions.getValue(function)
    if(func.returntype!=null)
        return func.returntype
    // function has return values, but the return type depends on the arguments

    return when (function) {
        "abs" -> {
            val dt = args.single().inferType(program)
            when(dt) {
                in ByteDatatypes -> DataType.UBYTE
                in WordDatatypes -> DataType.UWORD
                DataType.FLOAT -> DataType.FLOAT
                else -> throw FatalAstException("weird datatype passed to abs $dt")
            }
        }
        "max", "min" -> {
            val dt = datatypeFromIterableArg(args.single())
            when(dt) {
                in NumericDatatypes -> dt
                in StringDatatypes -> DataType.UBYTE
                in ArrayDatatypes -> ArrayElementTypes.getValue(dt)
                else -> null
            }
        }
        "sum" -> {
            when(datatypeFromIterableArg(args.single())) {
                DataType.UBYTE, DataType.UWORD -> DataType.UWORD
                DataType.BYTE, DataType.WORD -> DataType.WORD
                DataType.FLOAT -> DataType.FLOAT
                DataType.ARRAY_UB, DataType.ARRAY_UW -> DataType.UWORD
                DataType.ARRAY_B, DataType.ARRAY_W -> DataType.WORD
                DataType.ARRAY_F -> DataType.FLOAT
                in StringDatatypes -> DataType.UWORD
                else -> null
            }
        }
        "len" -> {
            // a length can be >255 so in that case, the result is an UWORD instead of an UBYTE
            // but to avoid a lot of code duplication we simply assume UWORD in all cases for now
            return DataType.UWORD
        }
        else -> return null
    }
}


class NotConstArgumentException: AstException("not a const argument to a built-in function")


private fun oneDoubleArg(args: List<IExpression>, position: Position, program: Program, function: (arg: Double)->Number): LiteralValue {
    if(args.size!=1)
        throw SyntaxError("built-in function requires one floating point argument", position)
    val constval = args[0].constValue(program) ?: throw NotConstArgumentException()
    if(constval.type!=DataType.FLOAT)
        throw SyntaxError("built-in function requires one floating point argument", position)

    val float = constval.asNumericValue?.toDouble()!!
    return numericLiteral(function(float), args[0].position)
}

private fun oneDoubleArgOutputWord(args: List<IExpression>, position: Position, program: Program, function: (arg: Double)->Number): LiteralValue {
    if(args.size!=1)
        throw SyntaxError("built-in function requires one floating point argument", position)
    val constval = args[0].constValue(program) ?: throw NotConstArgumentException()
    if(constval.type!=DataType.FLOAT)
        throw SyntaxError("built-in function requires one floating point argument", position)
    return LiteralValue(DataType.WORD, wordvalue=function(constval.asNumericValue!!.toDouble()).toInt(), position=args[0].position)
}

private fun oneIntArgOutputInt(args: List<IExpression>, position: Position, program: Program, function: (arg: Int)->Number): LiteralValue {
    if(args.size!=1)
        throw SyntaxError("built-in function requires one integer argument", position)
    val constval = args[0].constValue(program) ?: throw NotConstArgumentException()
    if(constval.type!=DataType.UBYTE && constval.type!=DataType.UWORD)
        throw SyntaxError("built-in function requires one integer argument", position)

    val integer = constval.asNumericValue?.toInt()!!
    return numericLiteral(function(integer).toInt(), args[0].position)
}

private fun collectionArgOutputNumber(args: List<IExpression>, position: Position,
                                      program: Program,
                                      function: (arg: Collection<Double>)->Number): LiteralValue {
    if(args.size!=1)
        throw SyntaxError("builtin function requires one non-scalar argument", position)
    val iterable = args[0].constValue(program) ?: throw NotConstArgumentException()

    val result = if(iterable.arrayvalue != null) {
        val constants = iterable.arrayvalue.map { it.constValue(program)?.asNumericValue }
        if(null in constants)
            throw NotConstArgumentException()
        function(constants.map { it!!.toDouble() }).toDouble()
    } else {
        when(iterable.type) {
            DataType.UBYTE, DataType.UWORD, DataType.FLOAT -> throw SyntaxError("function expects an iterable type", position)
            else -> {
                val heapId = iterable.heapId ?: throw FatalAstException("iterable value should be on the heap")
                val array = program.heap.get(heapId).array ?: throw SyntaxError("function expects an iterable type", position)
                function(array.map {
                    if(it.integer!=null)
                        it.integer.toDouble()
                    else
                        throw FatalAstException("cannot perform function over array that contains other values besides constant integers")
                })
            }
        }
    }
    return numericLiteral(result, args[0].position)
}

private fun collectionArgOutputBoolean(args: List<IExpression>, position: Position,
                                       program: Program,
                                       function: (arg: Collection<Double>)->Boolean): LiteralValue {
    if(args.size!=1)
        throw SyntaxError("builtin function requires one non-scalar argument", position)
    val iterable = args[0].constValue(program) ?: throw NotConstArgumentException()

    val result = if(iterable.arrayvalue != null) {
        val constants = iterable.arrayvalue.map { it.constValue(program)?.asNumericValue }
        if(null in constants)
            throw NotConstArgumentException()
        function(constants.map { it!!.toDouble() })
    } else {
        val array = program.heap.get(iterable.heapId!!).array ?: throw SyntaxError("function requires array argument", position)
        function(array.map {
            if(it.integer!=null)
                it.integer.toDouble()
            else
                throw FatalAstException("cannot perform function over array that contains other values besides constant integers")
        })
    }
    return LiteralValue.fromBoolean(result, position)
}

private fun builtinAbs(args: List<IExpression>, position: Position, program: Program): LiteralValue {
    // 1 arg, type = float or int, result type= isSameAs as argument type
    if(args.size!=1)
        throw SyntaxError("abs requires one numeric argument", position)

    val constval = args[0].constValue(program) ?: throw NotConstArgumentException()
    val number = constval.asNumericValue
    return when (number) {
        is Int, is Byte, is Short -> numericLiteral(abs(number.toInt()), args[0].position)
        is Double -> numericLiteral(abs(number.toDouble()), args[0].position)
        else -> throw SyntaxError("abs requires one numeric argument", position)
    }
}

private fun builtinAvg(args: List<IExpression>, position: Position, program: Program): LiteralValue {
    if(args.size!=1)
        throw SyntaxError("avg requires array argument", position)
    val iterable = args[0].constValue(program) ?: throw NotConstArgumentException()
    val result = if(iterable.arrayvalue!=null) {
        val constants = iterable.arrayvalue.map { it.constValue(program)?.asNumericValue }
        if (null in constants)
            throw NotConstArgumentException()
        (constants.map { it!!.toDouble() }).average()
    }
    else {
        val heapId = iterable.heapId!!
        val integerarray = program.heap.get(heapId).array
        if(integerarray!=null) {
            if (integerarray.all { it.integer != null }) {
                integerarray.map { it.integer!! }.average()
            } else {
                throw ExpressionError("cannot avg() over array that does not only contain constant numerical values", position)
            }
        } else {
            val doublearray = program.heap.get(heapId).doubleArray
            doublearray?.average() ?: throw SyntaxError("avg requires array argument", position)
        }
    }
    return numericLiteral(result, args[0].position)
}

private fun builtinStrlen(args: List<IExpression>, position: Position, program: Program): LiteralValue {
    if (args.size != 1)
        throw SyntaxError("strlen requires one argument", position)
    val argument = args[0].constValue(program) ?: throw NotConstArgumentException()
    if(argument.type !in StringDatatypes)
        throw SyntaxError("strlen must have string argument", position)
    val string = argument.strvalue(program.heap)
    val zeroIdx = string.indexOf('\u0000')
    return if(zeroIdx>=0)
        LiteralValue.optimalInteger(zeroIdx, position=position)
    else
        LiteralValue.optimalInteger(string.length, position=position)
}

private fun builtinLen(args: List<IExpression>, position: Position, program: Program): LiteralValue {
    // note: in some cases the length is > 255 and then we have to return a UWORD type instead of a UBYTE.
    if(args.size!=1)
        throw SyntaxError("len requires one argument", position)
    var argument = args[0].constValue(program)
    if(argument==null) {
        val directMemVar = ((args[0] as? DirectMemoryRead)?.addressExpression as? IdentifierReference)?.targetVarDecl(program.namespace)
        val arraySize = directMemVar?.arraysize?.size()
        if(arraySize != null)
            return LiteralValue.optimalInteger(arraySize, position)
        if(args[0] !is IdentifierReference)
            throw SyntaxError("len argument should be an identifier, but is ${args[0]}", position)
        val target = (args[0] as IdentifierReference).targetStatement(program.namespace)
        val argValue = (target as? VarDecl)?.value
        argument = argValue?.constValue(program)
                ?: throw NotConstArgumentException()
    }
    return when(argument.type) {
        DataType.ARRAY_UB, DataType.ARRAY_B, DataType.ARRAY_UW, DataType.ARRAY_W -> {
            val arraySize = argument.arrayvalue?.size ?: program.heap.get(argument.heapId!!).arraysize
            if(arraySize>256)
                throw CompilerException("array length exceeds byte limit ${argument.position}")
            LiteralValue.optimalInteger(arraySize, args[0].position)
        }
        DataType.ARRAY_F -> {
            val arraySize = argument.arrayvalue?.size ?: program.heap.get(argument.heapId!!).arraysize
            if(arraySize>256)
                throw CompilerException("array length exceeds byte limit ${argument.position}")
            LiteralValue.optimalInteger(arraySize, args[0].position)
        }
        in StringDatatypes -> {
            val str = argument.strvalue(program.heap)
            if(str.length>255)
                throw CompilerException("string length exceeds byte limit ${argument.position}")
            LiteralValue.optimalInteger(str.length, args[0].position)
        }
        in NumericDatatypes -> throw SyntaxError("len of weird argument ${args[0]}", position)
        else -> throw CompilerException("weird datatype")
    }
}


private fun builtinMkword(args: List<IExpression>, position: Position, program: Program): LiteralValue {
    if (args.size != 2)
        throw SyntaxError("mkword requires lsb and msb arguments", position)
    val constLsb = args[0].constValue(program) ?: throw NotConstArgumentException()
    val constMsb = args[1].constValue(program) ?: throw NotConstArgumentException()
    val result = (constMsb.asIntegerValue!! shl 8) or constLsb.asIntegerValue!!
    return LiteralValue(DataType.UWORD, wordvalue = result, position = position)
}

private fun builtinSin8(args: List<IExpression>, position: Position, program: Program): LiteralValue {
    if (args.size != 1)
        throw SyntaxError("sin8 requires one argument", position)
    val constval = args[0].constValue(program) ?: throw NotConstArgumentException()
    val rad = constval.asNumericValue!!.toDouble() /256.0 * 2.0 * PI
    return LiteralValue(DataType.BYTE, bytevalue = (127.0* sin(rad)).toShort(), position = position)
}

private fun builtinSin8u(args: List<IExpression>, position: Position, program: Program): LiteralValue {
    if (args.size != 1)
        throw SyntaxError("sin8u requires one argument", position)
    val constval = args[0].constValue(program) ?: throw NotConstArgumentException()
    val rad = constval.asNumericValue!!.toDouble() /256.0 * 2.0 * PI
    return LiteralValue(DataType.UBYTE, bytevalue = (128.0+127.5*sin(rad)).toShort(), position = position)
}

private fun builtinCos8(args: List<IExpression>, position: Position, program: Program): LiteralValue {
    if (args.size != 1)
        throw SyntaxError("cos8 requires one argument", position)
    val constval = args[0].constValue(program) ?: throw NotConstArgumentException()
    val rad = constval.asNumericValue!!.toDouble() /256.0 * 2.0 * PI
    return LiteralValue(DataType.BYTE, bytevalue = (127.0* cos(rad)).toShort(), position = position)
}

private fun builtinCos8u(args: List<IExpression>, position: Position, program: Program): LiteralValue {
    if (args.size != 1)
        throw SyntaxError("cos8u requires one argument", position)
    val constval = args[0].constValue(program) ?: throw NotConstArgumentException()
    val rad = constval.asNumericValue!!.toDouble() /256.0 * 2.0 * PI
    return LiteralValue(DataType.UBYTE, bytevalue = (128.0 + 127.5*cos(rad)).toShort(), position = position)
}

private fun builtinSin16(args: List<IExpression>, position: Position, program: Program): LiteralValue {
    if (args.size != 1)
        throw SyntaxError("sin16 requires one argument", position)
    val constval = args[0].constValue(program) ?: throw NotConstArgumentException()
    val rad = constval.asNumericValue!!.toDouble() /256.0 * 2.0 * PI
    return LiteralValue(DataType.WORD, wordvalue = (32767.0* sin(rad)).toInt(), position = position)
}

private fun builtinSin16u(args: List<IExpression>, position: Position, program: Program): LiteralValue {
    if (args.size != 1)
        throw SyntaxError("sin16u requires one argument", position)
    val constval = args[0].constValue(program) ?: throw NotConstArgumentException()
    val rad = constval.asNumericValue!!.toDouble() /256.0 * 2.0 * PI
    return LiteralValue(DataType.UWORD, wordvalue = (32768.0+32767.5*sin(rad)).toInt(), position = position)
}

private fun builtinCos16(args: List<IExpression>, position: Position, program: Program): LiteralValue {
    if (args.size != 1)
        throw SyntaxError("cos16 requires one argument", position)
    val constval = args[0].constValue(program) ?: throw NotConstArgumentException()
    val rad = constval.asNumericValue!!.toDouble() /256.0 * 2.0 * PI
    return LiteralValue(DataType.WORD, wordvalue = (32767.0* cos(rad)).toInt(), position = position)
}

private fun builtinCos16u(args: List<IExpression>, position: Position, program: Program): LiteralValue {
    if (args.size != 1)
        throw SyntaxError("cos16u requires one argument", position)
    val constval = args[0].constValue(program) ?: throw NotConstArgumentException()
    val rad = constval.asNumericValue!!.toDouble() /256.0 * 2.0 * PI
    return LiteralValue(DataType.UWORD, wordvalue = (32768.0+32767.5* cos(rad)).toInt(), position = position)
}

private fun numericLiteral(value: Number, position: Position): LiteralValue {
    val floatNum=value.toDouble()
    val tweakedValue: Number =
            if(floatNum== floor(floatNum) && (floatNum>=-32768 && floatNum<=65535))
                floatNum.toInt()  // we have an integer disguised as a float.
            else
                floatNum

    return when(tweakedValue) {
        is Int -> LiteralValue.optimalNumeric(value.toInt(), position)
        is Short -> LiteralValue.optimalNumeric(value.toInt(), position)
        is Byte -> LiteralValue(DataType.UBYTE, bytevalue = value.toShort(), position = position)
        is Double -> LiteralValue(DataType.FLOAT, floatvalue = value.toDouble(), position = position)
        is Float -> LiteralValue(DataType.FLOAT, floatvalue = value.toDouble(), position = position)
        else -> throw FatalAstException("invalid number type ${value::class}")
    }
}
