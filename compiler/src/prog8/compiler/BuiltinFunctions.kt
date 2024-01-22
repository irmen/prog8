package prog8.compiler

import prog8.ast.Program
import prog8.ast.base.AstException
import prog8.ast.base.FatalAstException
import prog8.ast.base.SyntaxError
import prog8.ast.expressions.*
import prog8.ast.statements.VarDecl
import prog8.code.core.*
import kotlin.math.*

private typealias ConstExpressionCaller = (args: List<Expression>, position: Position, program: Program) -> NumericLiteral

internal val constEvaluatorsForBuiltinFuncs: Map<String, ConstExpressionCaller> = mapOf(
    "abs" to ::builtinAbs,
    "len" to ::builtinLen,
    "sizeof" to ::builtinSizeof,
    "sgn" to ::builtinSgn,
    "sqrt__ubyte" to { a, p, prg -> oneIntArgOutputInt(a, p, prg, false) { sqrt(it.toDouble()) } },
    "sqrt__uword" to { a, p, prg -> oneIntArgOutputInt(a, p, prg, false) { sqrt(it.toDouble()) } },
    "sqrt__float" to { a, p, prg -> oneFloatArgOutputFloat(a, p, prg) { sqrt(it) } },
    "any" to { a, p, prg -> collectionArgBoolResult(a, p, prg) { array->array.any { it!=0.0 }  } },
    "all" to { a, p, prg -> collectionArgBoolResult(a, p, prg) { array->array.all { it!=0.0 }  } },
    "lsb" to { a, p, prg -> oneIntArgOutputInt(a, p, prg, true) { x: Int -> (x and 255).toDouble() } },
    "msb" to { a, p, prg -> oneIntArgOutputInt(a, p, prg, true) { x: Int -> (x ushr 8 and 255).toDouble()} },
    "mkword" to ::builtinMkword,
    "clamp__ubyte" to ::builtinClampUByte,
    "clamp__byte" to ::builtinClampByte,
    "clamp__uword" to ::builtinClampUWord,
    "clamp__word" to ::builtinClampWord,
    "min__ubyte" to ::builtinMinUByte,
    "min__byte" to ::builtinMinByte,
    "min__uword" to ::builtinMinUWord,
    "min__word" to ::builtinMinWord,
    "max__ubyte" to ::builtinMaxUByte,
    "max__byte" to ::builtinMaxByte,
    "max__uword" to ::builtinMaxUWord,
    "max__word" to ::builtinMaxWord
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
    val allowedDt = if(signed) IntegerDatatypes else arrayOf(DataType.UBYTE, DataType.UWORD)
    if(constval.type !in allowedDt)
        throw SyntaxError("built-in function requires one integer argument", position)

    val integer = constval.number.toInt()
    return NumericLiteral.optimalInteger(function(integer).toInt(), args[0].position)
}

private fun oneFloatArgOutputFloat(args: List<Expression>, position: Position, program: Program, function: (arg: Double)->Double): NumericLiteral {
    if(args.size!=1)
        throw SyntaxError("built-in function requires one float argument", position)
    val constval = args[0].constValue(program) ?: throw NotConstArgumentException()
    if(constval.type != DataType.FLOAT)
        throw SyntaxError("built-in function requires one float argument", position)

    return NumericLiteral(DataType.FLOAT, function(constval.number), args[0].position)
}

private fun collectionArgBoolResult(args: List<Expression>, position: Position, program: Program, function: (arg: List<Double>)->Boolean): NumericLiteral {
    if(args.size!=1)
        throw SyntaxError("builtin function requires one non-scalar argument", position)

    val array= args[0] as? ArrayLiteral ?: throw NotConstArgumentException()
    val constElements = array.value.map{it.constValue(program)?.number}
    if(constElements.contains(null))
        throw NotConstArgumentException()
    return NumericLiteral.fromBoolean(function(constElements.mapNotNull { it }), args[0].position)
}

private fun builtinAbs(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    // 1 arg, type = int, result type= uword
    if(args.size!=1)
        throw SyntaxError("abs requires one integer argument", position)

    val constval = args[0].constValue(program) ?: throw NotConstArgumentException()
    return when (constval.type) {
        in IntegerDatatypes -> NumericLiteral.optimalInteger(abs(constval.number.toInt()), args[0].position)
        else -> throw SyntaxError("abs requires one integer argument", position)
    }
}

private fun builtinSizeof(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    // 1 arg, type = anything, result type = ubyte or uword
    if(args.size!=1)
        throw SyntaxError("sizeof requires one argument", position)
    if(args[0] !is IdentifierReference && args[0] !is NumericLiteral)
        throw SyntaxError("sizeof argument should be an identifier or number", position)

    val dt = args[0].inferType(program)
    if(dt.isKnown) {
        if(args[0] is NumericLiteral)
            return NumericLiteral.optimalInteger(program.memsizer.memorySize(dt.getOr(DataType.UNDEFINED)), position)

        val target = (args[0] as IdentifierReference).targetStatement(program)
            ?: throw CannotEvaluateException("sizeof", "no target")

        return when {
            dt.isArray -> {
                val length = (target as VarDecl).arraysize?.constIndex() ?: throw CannotEvaluateException("sizeof", "unknown array size")
                val elementDt = ArrayToElementTypes.getValue(dt.getOr(DataType.UNDEFINED))
                NumericLiteral.optimalInteger(program.memsizer.memorySize(elementDt) * length, position)
            }
            dt istype DataType.STR -> throw SyntaxError("sizeof(str) is undefined, did you mean len, or perhaps string.length?", position)
            else -> NumericLiteral(DataType.UBYTE, program.memsizer.memorySize(dt.getOr(DataType.UNDEFINED)).toDouble(), position)
        }
    } else {
        throw SyntaxError("sizeof invalid argument type", position)
    }
}

private fun builtinLen(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    // note: in some cases the length is > 255, and then we have to return a UWORD type instead of a UBYTE.
    if(args.size!=1)
        throw SyntaxError("len requires one argument", position)

    val directMemVar = ((args[0] as? DirectMemoryRead)?.addressExpression as? IdentifierReference)?.targetVarDecl(program)
    var arraySize = directMemVar?.arraysize?.constIndex()
    if(arraySize != null)
        return NumericLiteral.optimalInteger(arraySize, position)
    if(args[0] is ArrayLiteral)
        return NumericLiteral.optimalInteger((args[0] as ArrayLiteral).value.size, position)
    if(args[0] !is IdentifierReference)
        throw SyntaxError("len argument should be an identifier", position)
    val target = (args[0] as IdentifierReference).targetVarDecl(program)
        ?: throw CannotEvaluateException("len", "no target vardecl")

    return when(target.datatype) {
        in ArrayDatatypes -> {
            arraySize = target.arraysize?.constIndex()
            if(arraySize==null)
                throw CannotEvaluateException("len", "arraysize unknown")
            NumericLiteral.optimalInteger(arraySize, args[0].position)
        }
        DataType.STR -> {
            val refLv = target.value as? StringLiteral ?: throw CannotEvaluateException("len", "stringsize unknown")
            NumericLiteral.optimalInteger(refLv.value.length, args[0].position)
        }
        in NumericDatatypes, DataType.BOOL -> throw SyntaxError("cannot use len on numeric value, did you mean sizeof?", args[0].position)
        else -> throw InternalCompilerException("weird datatype")
    }
}

private fun builtinMkword(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    if (args.size != 2)
        throw SyntaxError("mkword requires msb and lsb arguments", position)
    val constMsb = args[0].constValue(program) ?: throw NotConstArgumentException()
    val constLsb = args[1].constValue(program) ?: throw NotConstArgumentException()
    val result = (constMsb.number.toInt() shl 8) or constLsb.number.toInt()
    return NumericLiteral(DataType.UWORD, result.toDouble(), position)
}

private fun builtinSgn(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    if (args.size != 1)
        throw SyntaxError("sgn requires one argument", position)
    val constval = args[0].constValue(program) ?: throw NotConstArgumentException()
    return NumericLiteral(DataType.BYTE, constval.number.sign, position)
}

private fun builtinMinByte(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    if (args.size != 2)
        throw SyntaxError("min requires 2 arguments", position)
    val val1 = args[0].constValue(program) ?: throw NotConstArgumentException()
    val val2 = args[1].constValue(program) ?: throw NotConstArgumentException()
    val result = min(val1.number.toInt(), val2.number.toInt())
    return NumericLiteral(DataType.BYTE, result.toDouble(), position)
}

private fun builtinMinUByte(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    if (args.size != 2)
        throw SyntaxError("min requires 2 arguments", position)
    val val1 = args[0].constValue(program) ?: throw NotConstArgumentException()
    val val2 = args[1].constValue(program) ?: throw NotConstArgumentException()
    val result = min(val1.number.toInt(), val2.number.toInt())
    return NumericLiteral(DataType.UBYTE, result.toDouble(), position)
}

private fun builtinMinWord(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    if (args.size != 2)
        throw SyntaxError("min requires 2 arguments", position)
    val val1 = args[0].constValue(program) ?: throw NotConstArgumentException()
    val val2 = args[1].constValue(program) ?: throw NotConstArgumentException()
    val result = min(val1.number.toInt(), val2.number.toInt())
    return NumericLiteral(DataType.WORD, result.toDouble(), position)
}

private fun builtinMinUWord(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    if (args.size != 2)
        throw SyntaxError("min requires 2 arguments", position)
    val val1 = args[0].constValue(program) ?: throw NotConstArgumentException()
    val val2 = args[1].constValue(program) ?: throw NotConstArgumentException()
    val result = min(val1.number.toInt(), val2.number.toInt())
    return NumericLiteral(DataType.UWORD, result.toDouble(), position)
}

private fun builtinMaxByte(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    if (args.size != 2)
        throw SyntaxError("max requires 2 arguments", position)
    val val1 = args[0].constValue(program) ?: throw NotConstArgumentException()
    val val2 = args[1].constValue(program) ?: throw NotConstArgumentException()
    val result = max(val1.number.toInt(), val2.number.toInt())
    return NumericLiteral(DataType.BYTE, result.toDouble(), position)
}

private fun builtinMaxUByte(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    if (args.size != 2)
        throw SyntaxError("max requires 2 arguments", position)
    val val1 = args[0].constValue(program) ?: throw NotConstArgumentException()
    val val2 = args[1].constValue(program) ?: throw NotConstArgumentException()
    val result = max(val1.number.toInt(), val2.number.toInt())
    return NumericLiteral(DataType.UBYTE, result.toDouble(), position)
}

private fun builtinMaxWord(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    if (args.size != 2)
        throw SyntaxError("max requires 2 arguments", position)
    val val1 = args[0].constValue(program) ?: throw NotConstArgumentException()
    val val2 = args[1].constValue(program) ?: throw NotConstArgumentException()
    val result = max(val1.number.toInt(), val2.number.toInt())
    return NumericLiteral(DataType.WORD, result.toDouble(), position)
}

private fun builtinMaxUWord(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    if (args.size != 2)
        throw SyntaxError("max requires 2 arguments", position)
    val val1 = args[0].constValue(program) ?: throw NotConstArgumentException()
    val val2 = args[1].constValue(program) ?: throw NotConstArgumentException()
    val result = max(val1.number.toInt(), val2.number.toInt())
    return NumericLiteral(DataType.UWORD, result.toDouble(), position)
}

private fun builtinClampUByte(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    if(args.size!=3)
        throw SyntaxError("clamp requires 3 arguments", position)
    val value = args[0].constValue(program) ?: throw NotConstArgumentException()
    val minimum = args[1].constValue(program) ?: throw NotConstArgumentException()
    val maximum = args[2].constValue(program) ?: throw NotConstArgumentException()
    val result = min(max(value.number, minimum.number), maximum.number)
    return NumericLiteral(DataType.UBYTE, result, position)
}

private fun builtinClampByte(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    if(args.size!=3)
        throw SyntaxError("clamp requires 3 arguments", position)
    val value = args[0].constValue(program) ?: throw NotConstArgumentException()
    val minimum = args[1].constValue(program) ?: throw NotConstArgumentException()
    val maximum = args[2].constValue(program) ?: throw NotConstArgumentException()
    val result = min(max(value.number, minimum.number), maximum.number)
    return NumericLiteral(DataType.BYTE, result, position)
}

private fun builtinClampUWord(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    if(args.size!=3)
        throw SyntaxError("clamp requires 3 arguments", position)
    val value = args[0].constValue(program) ?: throw NotConstArgumentException()
    val minimum = args[1].constValue(program) ?: throw NotConstArgumentException()
    val maximum = args[2].constValue(program) ?: throw NotConstArgumentException()
    val result = min(max(value.number, minimum.number), maximum.number)
    return NumericLiteral(DataType.UWORD, result, position)
}

private fun builtinClampWord(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    if(args.size!=3)
        throw SyntaxError("clamp requires 3 arguments", position)
    val value = args[0].constValue(program) ?: throw NotConstArgumentException()
    val minimum = args[1].constValue(program) ?: throw NotConstArgumentException()
    val maximum = args[2].constValue(program) ?: throw NotConstArgumentException()
    val result = min(max(value.number, minimum.number), maximum.number)
    return NumericLiteral(DataType.WORD, result, position)
}

