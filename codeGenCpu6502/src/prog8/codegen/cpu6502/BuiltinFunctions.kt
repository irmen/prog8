package prog8.codegen.cpu6502

import prog8.code.core.*

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
                 val returnType: DataType?) {

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
    FSignature("cmp"         , false, listOf(FParam("value1", IntegerDatatypesNoBool), FParam("value2", NumericDatatypesNoBool)), null),
    FSignature("abs"         , true, listOf(FParam("value", IntegerDatatypesNoBool)), DataType.UWORD),
    FSignature("len"         , true, listOf(FParam("values", IterableDatatypes)), DataType.UWORD),
    // normal functions follow:
    FSignature("sizeof"      , true, listOf(FParam("object", DataType.values())), DataType.UBYTE),
    FSignature("sgn"         , true, listOf(FParam("value", NumericDatatypesNoBool)), DataType.BYTE),
    FSignature("sqrt16"      , true, listOf(FParam("value", arrayOf(DataType.UWORD))), DataType.UBYTE),
    FSignature("any"         , true, listOf(FParam("values", ArrayDatatypes)), DataType.UBYTE),
    FSignature("all"         , true, listOf(FParam("values", ArrayDatatypes)), DataType.UBYTE),
    FSignature("lsb"         , true, listOf(FParam("value", arrayOf(DataType.UWORD, DataType.WORD))), DataType.UBYTE),
    FSignature("msb"         , true, listOf(FParam("value", arrayOf(DataType.UWORD, DataType.WORD))), DataType.UBYTE),
    FSignature("mkword"      , true, listOf(FParam("msb", arrayOf(DataType.UBYTE)), FParam("lsb", arrayOf(DataType.UBYTE))), DataType.UWORD),
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
    FSignature("memory"      , true, listOf(FParam("name", arrayOf(DataType.STR)), FParam("size", arrayOf(DataType.UWORD)), FParam("alignment", arrayOf(DataType.UWORD))), DataType.UWORD),
    FSignature("callfar"     , false, listOf(FParam("bank", arrayOf(DataType.UBYTE)), FParam("address", arrayOf(DataType.UWORD)), FParam("arg", arrayOf(DataType.UWORD))), null),
    FSignature("callrom"     , false, listOf(FParam("bank", arrayOf(DataType.UBYTE)), FParam("address", arrayOf(DataType.UWORD)), FParam("arg", arrayOf(DataType.UWORD))), null),
)

val BuiltinFunctions = functionSignatures.associateBy { it.name }
