package prog8.compiler

import prog8.ast.Program
import prog8.ast.base.AstException
import prog8.ast.base.FatalAstException
import prog8.ast.base.SyntaxError
import prog8.ast.expressions.*
import prog8.ast.statements.VarDecl
import prog8.code.core.*
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sign
import kotlin.math.sqrt


private typealias ConstExpressionCaller = (args: List<Expression>, position: Position, program: Program) -> NumericLiteral

class ReturnConvention(val dt: DataType?, val reg: RegisterOrPair?, val floatFac1: Boolean)
class ParamConvention(val dt: DataType, val reg: RegisterOrPair?, val variable: Boolean)
class CallConvention(val params: List<ParamConvention>, val returns: ReturnConvention) {
    override fun toString(): String {
        val paramConvs =  params.mapIndexed { index, it ->
            when {
                it.reg!=null -> "$index:${it.reg}"
                it.variable -> "$index:variable"
                else -> "$index:???"
            }
        }
        val returnConv =
            when {
                returns.reg!=null -> returns.reg.toString()
                returns.floatFac1 -> "floatFAC1"
                else -> "<no returnvalue>"
            }
        return "CallConvention[" + paramConvs.joinToString() + " ; returns: $returnConv]"
    }
}

class FParam(val name: String, val possibleDatatypes: Array<DataType>)

class FSignature(val name: String,
                 val pure: Boolean,      // does it have side effects?
                 val parameters: List<FParam>,
                 val returnType: DataType?,
                 val constExpressionFunc: ConstExpressionCaller? = null) {

    fun callConvention(actualParamTypes: List<DataType>): CallConvention {
        val returns: ReturnConvention = when (returnType) {
            DataType.UBYTE, DataType.BYTE -> ReturnConvention(returnType, RegisterOrPair.A, false)
            DataType.UWORD, DataType.WORD -> ReturnConvention(returnType, RegisterOrPair.AY, false)
            DataType.FLOAT -> ReturnConvention(returnType, null, true)
            in PassByReferenceDatatypes -> ReturnConvention(returnType!!, RegisterOrPair.AY, false)
            null -> ReturnConvention(null, null, false)
            else -> {
                // return type depends on arg type
                when (val paramType = actualParamTypes.first()) {
                    DataType.UBYTE, DataType.BYTE -> ReturnConvention(paramType, RegisterOrPair.A, false)
                    DataType.UWORD, DataType.WORD -> ReturnConvention(paramType, RegisterOrPair.AY, false)
                    DataType.FLOAT -> ReturnConvention(paramType, null, true)
                    in PassByReferenceDatatypes -> ReturnConvention(paramType, RegisterOrPair.AY, false)
                    else -> ReturnConvention(paramType, null, false)
                }
            }
        }

        return when {
            actualParamTypes.isEmpty() -> CallConvention(emptyList(), returns)
            actualParamTypes.size==1 -> {
                // one parameter goes via register/registerpair
                val paramConv = when(val paramType = actualParamTypes[0]) {
                    DataType.UBYTE, DataType.BYTE -> ParamConvention(paramType, RegisterOrPair.A, false)
                    DataType.UWORD, DataType.WORD -> ParamConvention(paramType, RegisterOrPair.AY, false)
                    DataType.FLOAT -> ParamConvention(paramType, RegisterOrPair.AY, false)
                    in PassByReferenceDatatypes -> ParamConvention(paramType, RegisterOrPair.AY, false)
                    else -> ParamConvention(paramType, null, false)
                }
                CallConvention(listOf(paramConv), returns)
            }
            else -> {
                // multiple parameters go via variables
                val paramConvs = actualParamTypes.map { ParamConvention(it, null, true) }
                CallConvention(paramConvs, returns)
            }
        }
    }
}


private val functionSignatures: List<FSignature> = listOf(
    // this set of function have no return value and operate in-place:
    FSignature("rol"         , false, listOf(FParam("item", arrayOf(DataType.UBYTE, DataType.UWORD))), null),
    FSignature("ror"         , false, listOf(FParam("item", arrayOf(DataType.UBYTE, DataType.UWORD))), null),
    FSignature("rol2"        , false, listOf(FParam("item", arrayOf(DataType.UBYTE, DataType.UWORD))), null),
    FSignature("ror2"        , false, listOf(FParam("item", arrayOf(DataType.UBYTE, DataType.UWORD))), null),
    FSignature("sort"        , false, listOf(FParam("array", ArrayDatatypes)), null),
    FSignature("reverse"     , false, listOf(FParam("array", ArrayDatatypes)), null),
    // cmp returns a status in the carry flag, but not a proper return value
    FSignature("cmp"         , false, listOf(FParam("value1", IntegerDatatypes), FParam("value2", NumericDatatypes)), null),
    FSignature("abs"         , true, listOf(FParam("value", IntegerDatatypes)), DataType.UWORD, ::builtinAbs),
    FSignature("len"         , true, listOf(FParam("values", IterableDatatypes)), DataType.UWORD, ::builtinLen),
    // normal functions follow:
    FSignature("sizeof"      , true, listOf(FParam("object", DataType.values())), DataType.UBYTE, ::builtinSizeof),
    FSignature("sgn"         , true, listOf(FParam("value", NumericDatatypes)), DataType.BYTE, ::builtinSgn ),
    FSignature("sqrt16"      , true, listOf(FParam("value", arrayOf(DataType.UWORD))), DataType.UBYTE) { a, p, prg -> oneIntArgOutputInt(a, p, prg) { sqrt(it.toDouble()) } },
    FSignature("any"         , true, listOf(FParam("values", ArrayDatatypes)), DataType.UBYTE) { a, p, prg -> collectionArg(a, p, prg, ::builtinAny) },
    FSignature("all"         , true, listOf(FParam("values", ArrayDatatypes)), DataType.UBYTE) { a, p, prg -> collectionArg(a, p, prg, ::builtinAll) },
    FSignature("lsb"         , true, listOf(FParam("value", arrayOf(DataType.UWORD, DataType.WORD))), DataType.UBYTE) { a, p, prg -> oneIntArgOutputInt(a, p, prg) { x: Int -> (x and 255).toDouble() } },
    FSignature("msb"         , true, listOf(FParam("value", arrayOf(DataType.UWORD, DataType.WORD))), DataType.UBYTE) { a, p, prg -> oneIntArgOutputInt(a, p, prg) { x: Int -> (x ushr 8 and 255).toDouble()} },
    FSignature("mkword"      , true, listOf(FParam("msb", arrayOf(DataType.UBYTE)), FParam("lsb", arrayOf(DataType.UBYTE))), DataType.UWORD, ::builtinMkword),
    FSignature("peek"        , true, listOf(FParam("address", arrayOf(DataType.UWORD))), DataType.UBYTE),
    FSignature("peekw"       , true, listOf(FParam("address", arrayOf(DataType.UWORD))), DataType.UWORD),
    FSignature("poke"        , false, listOf(FParam("address", arrayOf(DataType.UWORD)), FParam("value", arrayOf(DataType.UBYTE))), null),
    FSignature("pokemon"     , false, listOf(FParam("address", arrayOf(DataType.UWORD)), FParam("value", arrayOf(DataType.UBYTE))), null),
    FSignature("pokew"       , false, listOf(FParam("address", arrayOf(DataType.UWORD)), FParam("value", arrayOf(DataType.UWORD))), null),
    FSignature("pop"         , false, listOf(FParam("target", ByteDatatypes)), null),
    FSignature("popw"        , false, listOf(FParam("target", WordDatatypes)), null),
    FSignature("push"        , false, listOf(FParam("value", ByteDatatypes)), null),
    FSignature("pushw"       , false, listOf(FParam("value", WordDatatypes)), null),
    FSignature("rsave"       , false, emptyList(), null),
    FSignature("rsavex"      , false, emptyList(), null),
    FSignature("rrestore"    , false, emptyList(), null),
    FSignature("rrestorex"   , false, emptyList(), null),
    FSignature("rnd"         , false, emptyList(), DataType.UBYTE),
    FSignature("rndw"        , false, emptyList(), DataType.UWORD),
    FSignature("memory"      , true, listOf(FParam("name", arrayOf(DataType.STR)), FParam("size", arrayOf(DataType.UWORD)), FParam("alignment", arrayOf(DataType.UWORD))), DataType.UWORD),
    FSignature("swap"        , false, listOf(FParam("first", NumericDatatypes), FParam("second", NumericDatatypes)), null),
    FSignature("callfar"     , false, listOf(FParam("bank", arrayOf(DataType.UBYTE)), FParam("address", arrayOf(DataType.UWORD)), FParam("arg", arrayOf(DataType.UWORD))), null),
    FSignature("callrom"     , false, listOf(FParam("bank", arrayOf(DataType.UBYTE)), FParam("address", arrayOf(DataType.UWORD)), FParam("arg", arrayOf(DataType.UWORD))), null),
)

val BuiltinFunctions = functionSignatures.associateBy { it.name }


private fun builtinAny(array: List<Double>): Double = if(array.any { it!=0.0 }) 1.0 else 0.0

private fun builtinAll(array: List<Double>): Double = if(array.all { it!=0.0 }) 1.0 else 0.0

fun builtinFunctionReturnType(function: String): InferredTypes.InferredType {
    if(function in arrayOf("set_carry", "set_irqd", "clear_carry", "clear_irqd"))
        return InferredTypes.InferredType.void()

    val func = BuiltinFunctions.getValue(function)
    if(func.returnType==null)
        return InferredTypes.InferredType.void()
    return InferredTypes.knownFor(func.returnType)
}


class NotConstArgumentException: AstException("not a const argument to a built-in function")
class CannotEvaluateException(func:String, msg: String): FatalAstException("cannot evaluate built-in function $func: $msg")


private fun oneDoubleArg(args: List<Expression>, position: Position, program: Program, function: (arg: Double)->Number): NumericLiteral {
    if(args.size!=1)
        throw SyntaxError("built-in function requires one floating point argument", position)
    val constval = args[0].constValue(program) ?: throw NotConstArgumentException()
    val float = constval.number
    return numericLiteral(function(float), args[0].position)
}

private fun oneDoubleArgOutputWord(args: List<Expression>, position: Position, program: Program, function: (arg: Double)->Double): NumericLiteral {
    if(args.size!=1)
        throw SyntaxError("built-in function requires one floating point argument", position)
    val constval = args[0].constValue(program) ?: throw NotConstArgumentException()
    return NumericLiteral(DataType.WORD, function(constval.number), args[0].position)
}

private fun oneIntArgOutputInt(args: List<Expression>, position: Position, program: Program, function: (arg: Int)->Double): NumericLiteral {
    if(args.size!=1)
        throw SyntaxError("built-in function requires one integer argument", position)
    val constval = args[0].constValue(program) ?: throw NotConstArgumentException()
    if(constval.type != DataType.UBYTE && constval.type!= DataType.UWORD)
        throw SyntaxError("built-in function requires one integer argument", position)

    val integer = constval.number.toInt()
    return numericLiteral(function(integer).toInt(), args[0].position)
}

private fun collectionArg(args: List<Expression>, position: Position, program: Program, function: (arg: List<Double>)->Double): NumericLiteral {
    if(args.size!=1)
        throw SyntaxError("builtin function requires one non-scalar argument", position)

    val array= args[0] as? ArrayLiteral ?: throw NotConstArgumentException()
    val constElements = array.value.map{it.constValue(program)?.number}
    if(constElements.contains(null))
        throw NotConstArgumentException()

    return NumericLiteral.optimalNumeric(function(constElements.mapNotNull { it }), args[0].position)
}

private fun builtinAbs(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    // 1 arg, type = int, result type= uword
    if(args.size!=1)
        throw SyntaxError("abs requires one integer argument", position)

    val constval = args[0].constValue(program) ?: throw NotConstArgumentException()
    return when (constval.type) {
        in IntegerDatatypes -> numericLiteral(abs(constval.number.toInt()), args[0].position)
        else -> throw SyntaxError("abs requires one integer argument", position)
    }
}

private fun builtinSizeof(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    // 1 arg, type = anything, result type = ubyte
    if(args.size!=1)
        throw SyntaxError("sizeof requires one argument", position)
    if(args[0] !is IdentifierReference)
        throw SyntaxError("sizeof argument should be an identifier", position)

    val dt = args[0].inferType(program)
    if(dt.isKnown) {
        val target = (args[0] as IdentifierReference).targetStatement(program)
            ?: throw CannotEvaluateException("sizeof", "no target")

        return when {
            dt.isArray -> {
                val length = (target as VarDecl).arraysize!!.constIndex() ?: throw CannotEvaluateException("sizeof", "unknown array size")
                val elementDt = ArrayToElementTypes.getValue(dt.getOr(DataType.UNDEFINED))
                numericLiteral(program.memsizer.memorySize(elementDt) * length, position)
            }
            dt istype DataType.STR -> throw SyntaxError("sizeof str is undefined, did you mean len?", position)
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
        DataType.ARRAY_UB, DataType.ARRAY_B, DataType.ARRAY_UW, DataType.ARRAY_W, DataType.ARRAY_F -> {
            arraySize = target.arraysize?.constIndex()
            if(arraySize==null)
                throw CannotEvaluateException("len", "arraysize unknown")
            NumericLiteral.optimalInteger(arraySize, args[0].position)
        }
        DataType.STR -> {
            val refLv = target.value as? StringLiteral ?: throw CannotEvaluateException("len", "stringsize unknown")
            NumericLiteral.optimalInteger(refLv.value.length, args[0].position)
        }
        in NumericDatatypes -> throw SyntaxError("cannot use len on numeric value, did you mean sizeof?", args[0].position)
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

private fun numericLiteral(value: Number, position: Position): NumericLiteral {
    val floatNum=value.toDouble()
    val tweakedValue: Number =
        if(floatNum== floor(floatNum) && (floatNum>=-32768 && floatNum<=65535))
            floatNum.toInt()  // we have an integer disguised as a float.
        else
            floatNum

    return when(tweakedValue) {
        is Int -> NumericLiteral.optimalInteger(value.toInt(), position)
        is Short -> NumericLiteral.optimalInteger(value.toInt(), position)
        is Byte -> NumericLiteral(DataType.UBYTE, value.toDouble(), position)
        is Double -> NumericLiteral(DataType.FLOAT, value.toDouble(), position)
        is Float -> NumericLiteral(DataType.FLOAT, value.toDouble(), position)
        else -> throw FatalAstException("invalid number type ${value::class}")
    }
}
