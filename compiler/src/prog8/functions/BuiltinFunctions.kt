package prog8.functions

import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.compiler.CompilerException
import kotlin.math.*


class BuiltinFunctionParam(val name: String, val possibleDatatypes: Set<DataType>)


typealias ConstExpressionCaller = (args: List<Expression>, position: Position, program: Program) -> NumericLiteralValue


class FunctionSignature(val pure: Boolean,      // does it have side effects?
                        val parameters: List<BuiltinFunctionParam>,
                        val returntype: DataType?,
                        val constExpressionFunc: ConstExpressionCaller? = null)


val BuiltinFunctions = mapOf(
        // this set of function have no return value and operate in-place:
    "rol"         to FunctionSignature(false, listOf(BuiltinFunctionParam("item", setOf(DataType.UBYTE, DataType.UWORD))), null),
    "ror"         to FunctionSignature(false, listOf(BuiltinFunctionParam("item", setOf(DataType.UBYTE, DataType.UWORD))), null),
    "rol2"        to FunctionSignature(false, listOf(BuiltinFunctionParam("item", setOf(DataType.UBYTE, DataType.UWORD))), null),
    "ror2"        to FunctionSignature(false, listOf(BuiltinFunctionParam("item", setOf(DataType.UBYTE, DataType.UWORD))), null),
    "lsl"         to FunctionSignature(false, listOf(BuiltinFunctionParam("item", IntegerDatatypes)), null),
    "lsr"         to FunctionSignature(false, listOf(BuiltinFunctionParam("item", IntegerDatatypes)), null),
    "sort"        to FunctionSignature(false, listOf(BuiltinFunctionParam("array", ArrayDatatypes)), null),
    "reverse"     to FunctionSignature(false, listOf(BuiltinFunctionParam("array", ArrayDatatypes)), null),
        // these few have a return value depending on the argument(s):
    "max"         to FunctionSignature(true, listOf(BuiltinFunctionParam("values", ArrayDatatypes)), null) { a, p, prg -> collectionArg(a, p, prg, ::builtinMax) },    // type depends on args
    "min"         to FunctionSignature(true, listOf(BuiltinFunctionParam("values", ArrayDatatypes)), null) { a, p, prg -> collectionArg(a, p, prg, ::builtinMin) },    // type depends on args
    "sum"         to FunctionSignature(true, listOf(BuiltinFunctionParam("values", ArrayDatatypes)), null) { a, p, prg -> collectionArg(a, p, prg, ::builtinSum) },      // type depends on args
    "abs"         to FunctionSignature(true, listOf(BuiltinFunctionParam("value", NumericDatatypes)), null, ::builtinAbs),      // type depends on argument
    "len"         to FunctionSignature(true, listOf(BuiltinFunctionParam("values", IterableDatatypes)), null, ::builtinLen),    // type is UBYTE or UWORD depending on actual length
        // normal functions follow:
    "sgn"         to FunctionSignature(true, listOf(BuiltinFunctionParam("value", NumericDatatypes)), DataType.BYTE, ::builtinSgn ),
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
    "round"       to FunctionSignature(true, listOf(BuiltinFunctionParam("value", setOf(DataType.FLOAT))), DataType.FLOAT) { a, p, prg -> oneDoubleArgOutputWord(a, p, prg, Math::round) },
    "floor"       to FunctionSignature(true, listOf(BuiltinFunctionParam("value", setOf(DataType.FLOAT))), DataType.FLOAT) { a, p, prg -> oneDoubleArgOutputWord(a, p, prg, Math::floor) },
    "ceil"        to FunctionSignature(true, listOf(BuiltinFunctionParam("value", setOf(DataType.FLOAT))), DataType.FLOAT) { a, p, prg -> oneDoubleArgOutputWord(a, p, prg, Math::ceil) },
    "any"         to FunctionSignature(true, listOf(BuiltinFunctionParam("values", ArrayDatatypes)), DataType.UBYTE) { a, p, prg -> collectionArg(a, p, prg, ::builtinAny) },
    "all"         to FunctionSignature(true, listOf(BuiltinFunctionParam("values", ArrayDatatypes)), DataType.UBYTE) { a, p, prg -> collectionArg(a, p, prg, ::builtinAll) },
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
                                                        BuiltinFunctionParam("from", IterableDatatypes + DataType.UWORD),
                                                        BuiltinFunctionParam("to", IterableDatatypes + DataType.UWORD),
                                                        BuiltinFunctionParam("numbytes", setOf(DataType.UBYTE))), null),
    "memset"      to FunctionSignature(false, listOf(
                                                        BuiltinFunctionParam("address", IterableDatatypes + DataType.UWORD),
                                                        BuiltinFunctionParam("numbytes", setOf(DataType.UWORD)),
                                                        BuiltinFunctionParam("bytevalue", ByteDatatypes)), null),
    "memsetw"     to FunctionSignature(false, listOf(
                                                        BuiltinFunctionParam("address", IterableDatatypes + DataType.UWORD),
                                                        BuiltinFunctionParam("numwords", setOf(DataType.UWORD)),
                                                        BuiltinFunctionParam("wordvalue", setOf(DataType.UWORD, DataType.WORD))), null),
    "strlen"      to FunctionSignature(true, listOf(BuiltinFunctionParam("string", StringDatatypes)), DataType.UBYTE, ::builtinStrlen)
)

fun builtinMax(array: List<Number>): Number = array.maxBy { it.toDouble() }!!

fun builtinMin(array: List<Number>): Number = array.minBy { it.toDouble() }!!

fun builtinSum(array: List<Number>): Number = array.sumByDouble { it.toDouble() }

fun builtinAny(array: List<Number>): Number = if(array.any { it.toDouble()!=0.0 }) 1 else 0

fun builtinAll(array: List<Number>): Number = if(array.all { it.toDouble()!=0.0 }) 1 else 0


fun builtinFunctionReturnType(function: String, args: List<Expression>, program: Program): InferredTypes.InferredType {

    fun datatypeFromIterableArg(arglist: Expression): DataType {
        if(arglist is ArrayLiteralValue) {
            val dt = arglist.value.map {it.inferType(program).typeOrElse(DataType.STRUCT)}.toSet()
            if(dt.any { it !in NumericDatatypes }) {
                throw FatalAstException("fuction $function only accepts array of numeric values")
            }
            if(DataType.FLOAT in dt) return DataType.FLOAT
            if(DataType.UWORD in dt) return DataType.UWORD
            if(DataType.WORD in dt) return DataType.WORD
            if(DataType.BYTE in dt) return DataType.BYTE
            return DataType.UBYTE
        }
        if(arglist is IdentifierReference) {
            val idt = arglist.inferType(program)
            if(!idt.isKnown)
                throw FatalAstException("couldn't determine type of iterable $arglist")
            return when(val dt = idt.typeOrElse(DataType.STRUCT)) {
                in NumericDatatypes -> dt
                in StringDatatypes -> dt
                in ArrayDatatypes -> ArrayElementTypes.getValue(dt)
                else -> throw FatalAstException("function '$function' requires one argument which is an iterable")
            }
        }
        throw FatalAstException("function '$function' requires one argument which is an iterable")
    }

    val func = BuiltinFunctions.getValue(function)
    if(func.returntype!=null)
        return InferredTypes.knownFor(func.returntype)
    // function has return values, but the return type depends on the arguments

    return when (function) {
        "abs" -> {
            val dt = args.single().inferType(program)
            if(dt.typeOrElse(DataType.STRUCT) in NumericDatatypes)
                return dt
            else
                throw FatalAstException("weird datatype passed to abs $dt")
        }
        "max", "min" -> {
            when(val dt = datatypeFromIterableArg(args.single())) {
                in NumericDatatypes -> InferredTypes.knownFor(dt)
                in StringDatatypes -> InferredTypes.knownFor(DataType.UBYTE)
                in ArrayDatatypes -> InferredTypes.knownFor(ArrayElementTypes.getValue(dt))
                else -> InferredTypes.unknown()
            }
        }
        "sum" -> {
            when(datatypeFromIterableArg(args.single())) {
                DataType.UBYTE, DataType.UWORD -> InferredTypes.knownFor(DataType.UWORD)
                DataType.BYTE, DataType.WORD -> InferredTypes.knownFor(DataType.WORD)
                DataType.FLOAT -> InferredTypes.knownFor(DataType.FLOAT)
                DataType.ARRAY_UB, DataType.ARRAY_UW -> InferredTypes.knownFor(DataType.UWORD)
                DataType.ARRAY_B, DataType.ARRAY_W -> InferredTypes.knownFor(DataType.WORD)
                DataType.ARRAY_F -> InferredTypes.knownFor(DataType.FLOAT)
                in StringDatatypes -> InferredTypes.knownFor(DataType.UWORD)
                else -> InferredTypes.unknown()
            }
        }
        "len" -> {
            // a length can be >255 so in that case, the result is an UWORD instead of an UBYTE
            // but to avoid a lot of code duplication we simply assume UWORD in all cases for now
            return InferredTypes.knownFor(DataType.UWORD)
        }
        else -> return InferredTypes.unknown()
    }
}


class NotConstArgumentException: AstException("not a const argument to a built-in function")


private fun oneDoubleArg(args: List<Expression>, position: Position, program: Program, function: (arg: Double)->Number): NumericLiteralValue {
    if(args.size!=1)
        throw SyntaxError("built-in function requires one floating point argument", position)
    val constval = args[0].constValue(program) ?: throw NotConstArgumentException()
    val float = constval.number.toDouble()
    return numericLiteral(function(float), args[0].position)
}

private fun oneDoubleArgOutputWord(args: List<Expression>, position: Position, program: Program, function: (arg: Double)->Number): NumericLiteralValue {
    if(args.size!=1)
        throw SyntaxError("built-in function requires one floating point argument", position)
    val constval = args[0].constValue(program) ?: throw NotConstArgumentException()
    val float = constval.number.toDouble()
    return NumericLiteralValue(DataType.WORD, function(float).toInt(), args[0].position)
}

private fun oneIntArgOutputInt(args: List<Expression>, position: Position, program: Program, function: (arg: Int)->Number): NumericLiteralValue {
    if(args.size!=1)
        throw SyntaxError("built-in function requires one integer argument", position)
    val constval = args[0].constValue(program) ?: throw NotConstArgumentException()
    if(constval.type != DataType.UBYTE && constval.type!= DataType.UWORD)
        throw SyntaxError("built-in function requires one integer argument", position)

    val integer = constval.number.toInt()
    return numericLiteral(function(integer).toInt(), args[0].position)
}

private fun collectionArg(args: List<Expression>, position: Position, program: Program, function: (arg: List<Number>)->Number): NumericLiteralValue {
    if(args.size!=1)
        throw SyntaxError("builtin function requires one non-scalar argument", position)

    val array= args[0] as? ArrayLiteralValue ?: throw NotConstArgumentException()
    val constElements = array.value.map{it.constValue(program)?.number}
    if(constElements.contains(null))
        throw NotConstArgumentException()

    return NumericLiteralValue.optimalNumeric(function(constElements.mapNotNull { it }), args[0].position)
}

private fun builtinAbs(args: List<Expression>, position: Position, program: Program): NumericLiteralValue {
    // 1 arg, type = float or int, result type= isSameAs as argument type
    if(args.size!=1)
        throw SyntaxError("abs requires one numeric argument", position)

    val constval = args[0].constValue(program) ?: throw NotConstArgumentException()
    return when (constval.type) {
        in IntegerDatatypes -> numericLiteral(abs(constval.number.toInt()), args[0].position)
        DataType.FLOAT -> numericLiteral(abs(constval.number.toDouble()), args[0].position)
        else -> throw SyntaxError("abs requires one numeric argument", position)
    }
}

private fun builtinStrlen(args: List<Expression>, position: Position, program: Program): NumericLiteralValue {
    if (args.size != 1)
        throw SyntaxError("strlen requires one argument", position)
    val argument = args[0].constValue(program) ?: throw NotConstArgumentException()
    if(argument.type !in StringDatatypes)
        throw SyntaxError("strlen must have string argument", position)

    throw NotConstArgumentException()       // this function is not considering the string argument a constant
}

private fun builtinLen(args: List<Expression>, position: Position, program: Program): NumericLiteralValue {
    // note: in some cases the length is > 255 and then we have to return a UWORD type instead of a UBYTE.
    if(args.size!=1)
        throw SyntaxError("len requires one argument", position)
    val constArg = args[0].constValue(program)
    if(constArg!=null)
        throw SyntaxError("len of weird argument ${args[0]}", position)

    val directMemVar = ((args[0] as? DirectMemoryRead)?.addressExpression as? IdentifierReference)?.targetVarDecl(program.namespace)
    var arraySize = directMemVar?.arraysize?.size()
    if(arraySize != null)
        return NumericLiteralValue.optimalInteger(arraySize, position)
    if(args[0] is ArrayLiteralValue)
        return NumericLiteralValue.optimalInteger((args[0] as ArrayLiteralValue).value.size, position)
    if(args[0] !is IdentifierReference)
        throw SyntaxError("len argument should be an identifier, but is ${args[0]}", position)
    val target = (args[0] as IdentifierReference).targetVarDecl(program.namespace)!!

    return when(target.datatype) {
        DataType.ARRAY_UB, DataType.ARRAY_B, DataType.ARRAY_UW, DataType.ARRAY_W -> {
            arraySize = target.arraysize!!.size()!!
            if(arraySize>256)
                throw CompilerException("array length exceeds byte limit ${target.position}")
            NumericLiteralValue.optimalInteger(arraySize, args[0].position)
        }
        DataType.ARRAY_F -> {
            arraySize = target.arraysize!!.size()!!
            if(arraySize>256)
                throw CompilerException("array length exceeds byte limit ${target.position}")
            NumericLiteralValue.optimalInteger(arraySize, args[0].position)
        }
        in StringDatatypes -> {
            val refLv = target.value as StringLiteralValue
            if(refLv.value.length>255)
                throw CompilerException("string length exceeds byte limit ${refLv.position}")
            NumericLiteralValue.optimalInteger(refLv.value.length, args[0].position)
        }
        in NumericDatatypes -> throw SyntaxError("len of weird argument ${args[0]}", position)
        else -> throw CompilerException("weird datatype")
    }
}


private fun builtinMkword(args: List<Expression>, position: Position, program: Program): NumericLiteralValue {
    if (args.size != 2)
        throw SyntaxError("mkword requires lsb and msb arguments", position)
    val constLsb = args[0].constValue(program) ?: throw NotConstArgumentException()
    val constMsb = args[1].constValue(program) ?: throw NotConstArgumentException()
    val result = (constMsb.number.toInt() shl 8) or constLsb.number.toInt()
    return NumericLiteralValue(DataType.UWORD, result, position)
}

private fun builtinSin8(args: List<Expression>, position: Position, program: Program): NumericLiteralValue {
    if (args.size != 1)
        throw SyntaxError("sin8 requires one argument", position)
    val constval = args[0].constValue(program) ?: throw NotConstArgumentException()
    val rad = constval.number.toDouble() /256.0 * 2.0 * PI
    return NumericLiteralValue(DataType.BYTE, (127.0 * sin(rad)).toShort(), position)
}

private fun builtinSin8u(args: List<Expression>, position: Position, program: Program): NumericLiteralValue {
    if (args.size != 1)
        throw SyntaxError("sin8u requires one argument", position)
    val constval = args[0].constValue(program) ?: throw NotConstArgumentException()
    val rad = constval.number.toDouble() /256.0 * 2.0 * PI
    return NumericLiteralValue(DataType.UBYTE, (128.0 + 127.5 * sin(rad)).toShort(), position)
}

private fun builtinCos8(args: List<Expression>, position: Position, program: Program): NumericLiteralValue {
    if (args.size != 1)
        throw SyntaxError("cos8 requires one argument", position)
    val constval = args[0].constValue(program) ?: throw NotConstArgumentException()
    val rad = constval.number.toDouble() /256.0 * 2.0 * PI
    return NumericLiteralValue(DataType.BYTE, (127.0 * cos(rad)).toShort(), position)
}

private fun builtinCos8u(args: List<Expression>, position: Position, program: Program): NumericLiteralValue {
    if (args.size != 1)
        throw SyntaxError("cos8u requires one argument", position)
    val constval = args[0].constValue(program) ?: throw NotConstArgumentException()
    val rad = constval.number.toDouble() /256.0 * 2.0 * PI
    return NumericLiteralValue(DataType.UBYTE, (128.0 + 127.5 * cos(rad)).toShort(), position)
}

private fun builtinSin16(args: List<Expression>, position: Position, program: Program): NumericLiteralValue {
    if (args.size != 1)
        throw SyntaxError("sin16 requires one argument", position)
    val constval = args[0].constValue(program) ?: throw NotConstArgumentException()
    val rad = constval.number.toDouble() /256.0 * 2.0 * PI
    return NumericLiteralValue(DataType.WORD, (32767.0 * sin(rad)).toInt(), position)
}

private fun builtinSin16u(args: List<Expression>, position: Position, program: Program): NumericLiteralValue {
    if (args.size != 1)
        throw SyntaxError("sin16u requires one argument", position)
    val constval = args[0].constValue(program) ?: throw NotConstArgumentException()
    val rad = constval.number.toDouble() /256.0 * 2.0 * PI
    return NumericLiteralValue(DataType.UWORD, (32768.0 + 32767.5 * sin(rad)).toInt(), position)
}

private fun builtinCos16(args: List<Expression>, position: Position, program: Program): NumericLiteralValue {
    if (args.size != 1)
        throw SyntaxError("cos16 requires one argument", position)
    val constval = args[0].constValue(program) ?: throw NotConstArgumentException()
    val rad = constval.number.toDouble() /256.0 * 2.0 * PI
    return NumericLiteralValue(DataType.WORD, (32767.0 * cos(rad)).toInt(), position)
}

private fun builtinCos16u(args: List<Expression>, position: Position, program: Program): NumericLiteralValue {
    if (args.size != 1)
        throw SyntaxError("cos16u requires one argument", position)
    val constval = args[0].constValue(program) ?: throw NotConstArgumentException()
    val rad = constval.number.toDouble() /256.0 * 2.0 * PI
    return NumericLiteralValue(DataType.UWORD, (32768.0 + 32767.5 * cos(rad)).toInt(), position)
}

private fun builtinSgn(args: List<Expression>, position: Position, program: Program): NumericLiteralValue {
    if (args.size != 1)
        throw SyntaxError("sgn requires one argument", position)
    val constval = args[0].constValue(program) ?: throw NotConstArgumentException()
    return NumericLiteralValue(DataType.BYTE, constval.number.toDouble().sign.toShort(), position)
}

private fun numericLiteral(value: Number, position: Position): NumericLiteralValue {
    val floatNum=value.toDouble()
    val tweakedValue: Number =
            if(floatNum== floor(floatNum) && (floatNum>=-32768 && floatNum<=65535))
                floatNum.toInt()  // we have an integer disguised as a float.
            else
                floatNum

    return when(tweakedValue) {
        is Int -> NumericLiteralValue.optimalNumeric(value.toInt(), position)
        is Short -> NumericLiteralValue.optimalNumeric(value.toInt(), position)
        is Byte -> NumericLiteralValue(DataType.UBYTE, value.toShort(), position)
        is Double -> NumericLiteralValue(DataType.FLOAT, value.toDouble(), position)
        is Float -> NumericLiteralValue(DataType.FLOAT, value.toDouble(), position)
        else -> throw FatalAstException("invalid number type ${value::class}")
    }
}
