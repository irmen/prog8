package prog8.compiler

import prog8.ast.AstException
import prog8.ast.FatalAstException
import prog8.ast.Program
import prog8.ast.SyntaxError
import prog8.ast.expressions.*
import prog8.ast.statements.StructDecl
import prog8.ast.statements.VarDecl
import prog8.code.core.*
import kotlin.math.*

private typealias ConstExpressionCaller = (args: List<Expression>, position: Position, program: Program) -> NumericLiteral

internal val constEvaluatorsForBuiltinFuncs: Map<String, ConstExpressionCaller> = mapOf(
    "abs" to ::builtinAbs,
    "len" to ::builtinLen,
    "sizeof" to ::builtinSizeof,
    "offsetof" to ::builtinOffsetof,
    "sgn" to ::builtinSgn,
    "sqrt__ubyte" to { a, p, prg -> oneIntArgOutputInt(a, p, prg, false) { sqrt(it.toDouble()) } },
    "sqrt__uword" to { a, p, prg -> oneIntArgOutputInt(a, p, prg, false) { sqrt(it.toDouble()) } },
    "sqrt__float" to { a, p, prg -> oneFloatArgOutputFloat(a, p, prg) { sqrt(it) } },
    "lsb" to { a, p, prg -> oneIntArgOutputInt(a, p, prg, true) { x: Int -> (x and 255).toDouble() } },
    "lsw" to { a, p, prg -> oneIntArgOutputInt(a, p, prg, true) { x: Int -> (x and 65535).toDouble() } },
    "msb" to { a, p, prg -> oneIntArgOutputInt(a, p, prg, true) { x: Int -> (x ushr 8 and 255).toDouble()} },
    "msw" to { a, p, prg -> oneIntArgOutputInt(a, p, prg, true) { x: Int -> (x ushr 16 and 65535).toDouble()} },
    "mkword" to ::builtinMkword,
    "mklong" to ::builtinMklong,
    "mklong2" to ::builtinMklong,
    "clamp__ubyte" to ::builtinClampUByte,
    "clamp__byte" to ::builtinClampByte,
    "clamp__uword" to ::builtinClampUWord,
    "clamp__word" to ::builtinClampWord,
    "min__ubyte" to ::builtinMinUByte,
    "min__byte" to ::builtinMinByte,
    "min__uword" to ::builtinMinUWord,
    "min__word" to ::builtinMinWord,
    "min__long" to ::builtinMinLong,
    "max__ubyte" to ::builtinMaxUByte,
    "max__byte" to ::builtinMaxByte,
    "max__uword" to ::builtinMaxUWord,
    "max__word" to ::builtinMaxWord,
    "max__long" to ::builtinMaxLong
)

internal fun builtinFunctionReturnType(function: String): InferredTypes.InferredType {
    if(function in arrayOf("set_carry", "set_irqd", "clear_carry", "clear_irqd"))
        return InferredTypes.InferredType.void()

    val func = BuiltinFunctions.getValue(function)
    val returnType = func.returnType
    return if(returnType==null)
        InferredTypes.InferredType.void()
    else
        InferredTypes.knownFor(returnType)
}


internal class NotConstArgumentException: AstException("not a const argument to a built-in function")
internal class CannotEvaluateException(func:String, msg: String): FatalAstException("cannot evaluate built-in function $func: $msg")


private fun oneIntArgOutputInt(args: List<Expression>, position: Position, program: Program, signed: Boolean, function: (arg: Int)->Double): NumericLiteral {
    if(args.size!=1)
        throw SyntaxError("built-in function requires one integer argument", position)
    val constval = args[0].constValue(program) ?: throw NotConstArgumentException()
    if(signed) {
        if(!constval.type.isInteger)
            throw SyntaxError("built-in function requires one integer argument", position)
    } else {
        if(constval.type!=BaseDataType.UBYTE && constval.type!=BaseDataType.UWORD)
            throw SyntaxError("built-in function requires one integer argument", position)
    }
    val integer = constval.number.toInt()
    return NumericLiteral.optimalInteger(function(integer).toInt(), args[0].position)
}

private fun oneFloatArgOutputFloat(args: List<Expression>, position: Position, program: Program, function: (arg: Double)->Double): NumericLiteral {
    if(args.size!=1)
        throw SyntaxError("built-in function requires one float argument", position)
    val constval = args[0].constValue(program) ?: throw NotConstArgumentException()
    if(constval.type != BaseDataType.FLOAT)
        throw SyntaxError("built-in function requires one float argument", position)

    return NumericLiteral(BaseDataType.FLOAT, function(constval.number), args[0].position)
}

private fun builtinAbs(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    // 1 arg, type = int, result type= uword
    if(args.size!=1)
        throw SyntaxError("abs requires one integer argument", position)

    val constval = args[0].constValue(program) ?: throw NotConstArgumentException()
    return if (constval.type.isInteger) NumericLiteral.optimalInteger(abs(constval.number.toInt()), args[0].position)
    else throw SyntaxError("abs requires one integer argument", position)
}

private fun builtinOffsetof(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    // 1 arg, "Struct.field"
    if(args.size!=1)
        throw SyntaxError("offsetof requires one argument", position)
    val identifier = (args[0] as? IdentifierReference)?.nameInSource
    if(identifier==null || identifier.size<2)
        throw CannotEvaluateException("offsetof","argument should be an identifier of the form Struct.field")

    val structname = identifier.dropLast(1)
    val fieldname = identifier.last()
    val struct = args[0].definingScope.lookup(structname) as? StructDecl
    if(struct==null)
        throw SyntaxError("cannot find struct '$structname'", args[0].position)
    val offset = struct.offsetof(fieldname, program.memsizer)
    if(offset==null)
        throw SyntaxError("no such field '${identifier.joinToString(".")}'", args[0].position)
    return NumericLiteral.optimalInteger(offset.toInt(), position)
}

private fun builtinSizeof(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    // 1 arg, type = anything, result type = ubyte or uword
    if(args.size!=1)
        throw SyntaxError("sizeof requires one argument", position)
    if(args[0] !is IdentifierReference && args[0] !is NumericLiteral && args[0] !is AddressOf)
        throw CannotEvaluateException("sizeof","argument should be an identifier, number, or type name")

    val dt = args[0].inferType(program)
    if(dt.isKnown) {
        if(args[0] is NumericLiteral || args[0] is AddressOf)
            return NumericLiteral.optimalInteger(program.memsizer.memorySize(dt.getOrUndef(), null), position)

        val target = (args[0] as? IdentifierReference)?.targetStatement()
            ?: throw SyntaxError("wrong argument type", position)

        return when {
            dt.isArray -> {
                val length = (target as VarDecl).arraysize?.constIndex() ?: throw CannotEvaluateException("sizeof", "unknown array size")
                val elementDt = dt.getOrUndef().elementType()
                NumericLiteral.optimalInteger(program.memsizer.memorySize(elementDt, length), position)
            }
            dt.isString -> throw SyntaxError("sizeof(str) is undefined, did you mean len, or perhaps strings.length?", position)
            else -> NumericLiteral.optimalInteger( program.memsizer.memorySize(dt.getOrUndef(), null), position)
        }
    } else {
        val identifier = args[0] as? IdentifierReference
        if(identifier?.nameInSource?.size==1) {
            when(identifier.nameInSource[0]) {
                "ubyte" -> return NumericLiteral.optimalInteger(program.memsizer.memorySize(BaseDataType.UBYTE), position)
                "byte" -> return NumericLiteral.optimalInteger(program.memsizer.memorySize(BaseDataType.BYTE), position)
                "uword" -> return NumericLiteral.optimalInteger(program.memsizer.memorySize(BaseDataType.UWORD), position)
                "word" -> return NumericLiteral.optimalInteger(program.memsizer.memorySize(BaseDataType.WORD), position)
                "long" -> return NumericLiteral.optimalInteger(program.memsizer.memorySize(BaseDataType.LONG), position)
                "float" -> return NumericLiteral.optimalInteger(program.memsizer.memorySize(BaseDataType.FLOAT), position)
                "bool" -> return NumericLiteral.optimalInteger(program.memsizer.memorySize(BaseDataType.BOOL), position)
            }
        }

        // the argument could refer to a struct declaration
        val struct = (args[0] as? IdentifierReference)?.targetStructDecl()
        if(struct!=null) {
            val size = struct.memsize(program.memsizer)
            return NumericLiteral(BaseDataType.UBYTE, size.toDouble(), position)
        }

        throw SyntaxError("sizeof argument should be an identifier, number, or type name", position)
    }
}

private fun builtinLen(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    // note: in some cases the length is > 255, and then we have to return a UWORD type instead of a UBYTE.
    if(args.size!=1)
        throw SyntaxError("len requires one argument", position)

    val directMemVar = ((args[0] as? DirectMemoryRead)?.addressExpression as? IdentifierReference)?.targetVarDecl()
    var arraySize = directMemVar?.arraysize?.constIndex()
    if(arraySize != null)
        return NumericLiteral.optimalInteger(arraySize, position)
    if(args[0] is ArrayLiteral)
        return NumericLiteral.optimalInteger((args[0] as ArrayLiteral).value.size, position)
    if(args[0] is StringLiteral)
        return NumericLiteral.optimalInteger((args[0] as StringLiteral).value.length, position)
    if(args[0] !is IdentifierReference)
        throw SyntaxError("len argument should be an identifier", position)
    val target = (args[0] as IdentifierReference).targetVarDecl()
        ?: throw CannotEvaluateException("len", "no target vardecl")

    return when  {
        target.datatype.isArray -> {
            arraySize = target.arraysize?.constIndex()
            if(arraySize==null)
                throw CannotEvaluateException("len", "arraysize unknown")
            NumericLiteral.optimalInteger(arraySize, args[0].position)
        }
        target.datatype.isString -> {
            val refLv = target.value as? StringLiteral ?: throw CannotEvaluateException("len", "stringsize unknown")
            NumericLiteral.optimalInteger(refLv.value.length, args[0].position)
        }
        target.datatype.isNumericOrBool -> throw SyntaxError("cannot use len on numeric value, did you mean sizeof?", args[0].position)
        else -> throw InternalCompilerException("weird datatype")
    }
}

private fun builtinMkword(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    if (args.size != 2)
        throw SyntaxError("mkword requires msb and lsb arguments", position)
    val constMsb = args[0].constValue(program) ?: throw NotConstArgumentException()
    val constLsb = args[1].constValue(program) ?: throw NotConstArgumentException()
    val result = (constMsb.number.toInt() shl 8) or constLsb.number.toInt()
    return NumericLiteral(BaseDataType.UWORD, result.toDouble(), position)
}

private fun builtinMklong(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    when(args.size) {
        2 -> {
            val constMsw = args[0].constValue(program) ?: throw NotConstArgumentException()
            val constLsw = args[1].constValue(program) ?: throw NotConstArgumentException()
            val result = (constMsw.number.toInt() shl 16) or constLsw.number.toInt()
            return NumericLiteral(BaseDataType.LONG, result.toDouble(), position)
        }
        4 -> {
            val constMsb = args[0].constValue(program) ?: throw NotConstArgumentException()
            val constB2 = args[1].constValue(program) ?: throw NotConstArgumentException()
            val constB1 = args[2].constValue(program) ?: throw NotConstArgumentException()
            val constLsb = args[3].constValue(program) ?: throw NotConstArgumentException()
            val result = (constMsb.number.toInt() shl 24) or
                    (constB2.number.toInt() shl 16) or
                    (constB1.number.toInt() shl 8) or
                    constLsb.number.toInt()
            return NumericLiteral(BaseDataType.LONG, result.toDouble(), position)
        }
        else -> throw SyntaxError("mkword requires msw and lsw, or msb,b2,b1,lsb arguments", position)
    }
}

private fun builtinSgn(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    if (args.size != 1)
        throw SyntaxError("sgn requires one argument", position)
    val constval = args[0].constValue(program) ?: throw NotConstArgumentException()
    return NumericLiteral(BaseDataType.BYTE, constval.number.sign, position)
}

private fun builtinMinByte(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    if (args.size != 2)
        throw SyntaxError("min requires 2 arguments", position)
    val val1 = args[0].constValue(program) ?: throw NotConstArgumentException()
    val val2 = args[1].constValue(program) ?: throw NotConstArgumentException()
    val result = min(val1.number.toInt(), val2.number.toInt())
    return NumericLiteral(BaseDataType.BYTE, result.toDouble(), position)
}

private fun builtinMinUByte(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    if (args.size != 2)
        throw SyntaxError("min requires 2 arguments", position)
    val val1 = args[0].constValue(program) ?: throw NotConstArgumentException()
    val val2 = args[1].constValue(program) ?: throw NotConstArgumentException()
    val result = min(val1.number.toInt(), val2.number.toInt())
    return NumericLiteral(BaseDataType.UBYTE, result.toDouble(), position)
}

private fun builtinMinWord(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    if (args.size != 2)
        throw SyntaxError("min requires 2 arguments", position)
    val val1 = args[0].constValue(program) ?: throw NotConstArgumentException()
    val val2 = args[1].constValue(program) ?: throw NotConstArgumentException()
    val result = min(val1.number.toInt(), val2.number.toInt())
    return NumericLiteral(BaseDataType.WORD, result.toDouble(), position)
}

private fun builtinMinUWord(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    if (args.size != 2)
        throw SyntaxError("min requires 2 arguments", position)
    val val1 = args[0].constValue(program) ?: throw NotConstArgumentException()
    val val2 = args[1].constValue(program) ?: throw NotConstArgumentException()
    val result = min(val1.number.toInt(), val2.number.toInt())
    return NumericLiteral(BaseDataType.UWORD, result.toDouble(), position)
}

private fun builtinMinLong(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    if (args.size != 2)
        throw SyntaxError("min requires 2 arguments", position)
    val val1 = args[0].constValue(program) ?: throw NotConstArgumentException()
    val val2 = args[1].constValue(program) ?: throw NotConstArgumentException()
    val result = min(val1.number.toInt(), val2.number.toInt())
    return NumericLiteral(BaseDataType.LONG, result.toDouble(), position)
}

private fun builtinMaxByte(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    if (args.size != 2)
        throw SyntaxError("max requires 2 arguments", position)
    val val1 = args[0].constValue(program) ?: throw NotConstArgumentException()
    val val2 = args[1].constValue(program) ?: throw NotConstArgumentException()
    val result = max(val1.number.toInt(), val2.number.toInt())
    return NumericLiteral(BaseDataType.BYTE, result.toDouble(), position)
}

private fun builtinMaxUByte(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    if (args.size != 2)
        throw SyntaxError("max requires 2 arguments", position)
    val val1 = args[0].constValue(program) ?: throw NotConstArgumentException()
    val val2 = args[1].constValue(program) ?: throw NotConstArgumentException()
    val result = max(val1.number.toInt(), val2.number.toInt())
    return NumericLiteral(BaseDataType.UBYTE, result.toDouble(), position)
}

private fun builtinMaxWord(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    if (args.size != 2)
        throw SyntaxError("max requires 2 arguments", position)
    val val1 = args[0].constValue(program) ?: throw NotConstArgumentException()
    val val2 = args[1].constValue(program) ?: throw NotConstArgumentException()
    val result = max(val1.number.toInt(), val2.number.toInt())
    return NumericLiteral(BaseDataType.WORD, result.toDouble(), position)
}

private fun builtinMaxUWord(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    if (args.size != 2)
        throw SyntaxError("max requires 2 arguments", position)
    val val1 = args[0].constValue(program) ?: throw NotConstArgumentException()
    val val2 = args[1].constValue(program) ?: throw NotConstArgumentException()
    val result = max(val1.number.toInt(), val2.number.toInt())
    return NumericLiteral(BaseDataType.UWORD, result.toDouble(), position)
}

private fun builtinMaxLong(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    if (args.size != 2)
        throw SyntaxError("max requires 2 arguments", position)
    val val1 = args[0].constValue(program) ?: throw NotConstArgumentException()
    val val2 = args[1].constValue(program) ?: throw NotConstArgumentException()
    val result = max(val1.number.toInt(), val2.number.toInt())
    return NumericLiteral(BaseDataType.LONG, result.toDouble(), position)
}

private fun builtinClampUByte(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    if(args.size!=3)
        throw SyntaxError("clamp requires 3 arguments", position)
    val value = args[0].constValue(program) ?: throw NotConstArgumentException()
    val minimum = args[1].constValue(program) ?: throw NotConstArgumentException()
    val maximum = args[2].constValue(program) ?: throw NotConstArgumentException()
    val result = min(max(value.number, minimum.number), maximum.number)
    return NumericLiteral(BaseDataType.UBYTE, result, position)
}

private fun builtinClampByte(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    if(args.size!=3)
        throw SyntaxError("clamp requires 3 arguments", position)
    val value = args[0].constValue(program) ?: throw NotConstArgumentException()
    val minimum = args[1].constValue(program) ?: throw NotConstArgumentException()
    val maximum = args[2].constValue(program) ?: throw NotConstArgumentException()
    val result = min(max(value.number, minimum.number), maximum.number)
    return NumericLiteral(BaseDataType.BYTE, result, position)
}

private fun builtinClampUWord(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    if(args.size!=3)
        throw SyntaxError("clamp requires 3 arguments", position)
    val value = args[0].constValue(program) ?: throw NotConstArgumentException()
    val minimum = args[1].constValue(program) ?: throw NotConstArgumentException()
    val maximum = args[2].constValue(program) ?: throw NotConstArgumentException()
    val result = min(max(value.number, minimum.number), maximum.number)
    return NumericLiteral(BaseDataType.UWORD, result, position)
}

private fun builtinClampWord(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    if(args.size!=3)
        throw SyntaxError("clamp requires 3 arguments", position)
    val value = args[0].constValue(program) ?: throw NotConstArgumentException()
    val minimum = args[1].constValue(program) ?: throw NotConstArgumentException()
    val maximum = args[2].constValue(program) ?: throw NotConstArgumentException()
    val result = min(max(value.number, minimum.number), maximum.number)
    return NumericLiteral(BaseDataType.WORD, result, position)
}

