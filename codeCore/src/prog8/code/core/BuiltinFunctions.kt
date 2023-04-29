package prog8.code.core

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

class FSignature(val pure: Boolean,      // does it have side effects?
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

val BuiltinFunctions: Map<String, FSignature> = mapOf(
    // this set of function have no return value and operate in-place:
    "rol"       to FSignature(false, listOf(FParam("item", arrayOf(DataType.UBYTE, DataType.UWORD))), null),
    "ror"       to FSignature(false, listOf(FParam("item", arrayOf(DataType.UBYTE, DataType.UWORD))), null),
    "rol2"      to FSignature(false, listOf(FParam("item", arrayOf(DataType.UBYTE, DataType.UWORD))), null),
    "ror2"      to FSignature(false, listOf(FParam("item", arrayOf(DataType.UBYTE, DataType.UWORD))), null),
    "sort"      to FSignature(false, listOf(FParam("array", ArrayDatatypes)), null),
    "reverse"   to FSignature(false, listOf(FParam("array", ArrayDatatypes)), null),
    // cmp returns a status in the carry flag, but not a proper return value
    "cmp"       to FSignature(false, listOf(FParam("value1", IntegerDatatypesNoBool), FParam("value2", NumericDatatypesNoBool)), null),
    "prog8_lib_stringcompare"       to FSignature(true, listOf(FParam("str1", arrayOf(DataType.STR)), FParam("str2", arrayOf(DataType.STR))), DataType.BYTE),
    "abs"       to FSignature(true, listOf(FParam("value", IntegerDatatypesNoBool)), DataType.UWORD),
    "len"       to FSignature(true, listOf(FParam("values", IterableDatatypes)), DataType.UWORD),
    // normal functions follow:
    "sizeof"    to FSignature(true, listOf(FParam("object", DataType.values())), DataType.UBYTE),
    "sgn"       to FSignature(true, listOf(FParam("value", NumericDatatypesNoBool)), DataType.BYTE),
    "sqrt"      to FSignature(true, listOf(FParam("value", arrayOf(DataType.UWORD))), DataType.UBYTE),
    "divmod"    to FSignature(false, listOf(FParam("number", arrayOf(DataType.UBYTE)), FParam("divident", arrayOf(DataType.UBYTE)), FParam("division", arrayOf(DataType.UBYTE)), FParam("remainder", arrayOf(DataType.UBYTE))), null),
    "divmodw"   to FSignature(false, listOf(FParam("number", arrayOf(DataType.UWORD)), FParam("divident", arrayOf(DataType.UWORD)), FParam("division", arrayOf(DataType.UWORD)), FParam("remainder", arrayOf(DataType.UWORD))), null),
    "any"       to FSignature(true, listOf(FParam("values", ArrayDatatypes)), DataType.UBYTE),
    "all"       to FSignature(true, listOf(FParam("values", ArrayDatatypes)), DataType.UBYTE),
    "lsb"       to FSignature(true, listOf(FParam("value", arrayOf(DataType.UWORD, DataType.WORD))), DataType.UBYTE),
    "msb"       to FSignature(true, listOf(FParam("value", arrayOf(DataType.UWORD, DataType.WORD))), DataType.UBYTE),
    "mkword"    to FSignature(true, listOf(FParam("msb", arrayOf(DataType.UBYTE)), FParam("lsb", arrayOf(DataType.UBYTE))), DataType.UWORD),
    "min"       to FSignature(true, listOf(), null),
    "max"       to FSignature(true, listOf(), null),
    "min__byte"  to FSignature(true, listOf(FParam("val1", arrayOf(DataType.BYTE)), FParam("val2", arrayOf(DataType.BYTE))), DataType.BYTE),
    "min__ubyte" to FSignature(true, listOf(FParam("val1", arrayOf(DataType.UBYTE)), FParam("val2", arrayOf(DataType.UBYTE))), DataType.UBYTE),
    "min__word"  to FSignature(true, listOf(FParam("val1", arrayOf(DataType.WORD)), FParam("val2", arrayOf(DataType.WORD))), DataType.WORD),
    "min__uword" to FSignature(true, listOf(FParam("val1", arrayOf(DataType.UWORD)), FParam("val2", arrayOf(DataType.UWORD))), DataType.UWORD),
    "max__byte"  to FSignature(true, listOf(FParam("val1", arrayOf(DataType.BYTE)), FParam("val2", arrayOf(DataType.BYTE))), DataType.BYTE),
    "max__ubyte" to FSignature(true, listOf(FParam("val1", arrayOf(DataType.UBYTE)), FParam("val2", arrayOf(DataType.UBYTE))), DataType.UBYTE),
    "max__word"  to FSignature(true, listOf(FParam("val1", arrayOf(DataType.WORD)), FParam("val2", arrayOf(DataType.WORD))), DataType.WORD),
    "max__uword" to FSignature(true, listOf(FParam("val1", arrayOf(DataType.UWORD)), FParam("val2", arrayOf(DataType.UWORD))), DataType.UWORD),
    "peek"      to FSignature(true, listOf(FParam("address", arrayOf(DataType.UWORD))), DataType.UBYTE),
    "peekw"     to FSignature(true, listOf(FParam("address", arrayOf(DataType.UWORD))), DataType.UWORD),
    "poke"      to FSignature(false, listOf(FParam("address", arrayOf(DataType.UWORD)), FParam("value", arrayOf(DataType.UBYTE))), null),
    "pokemon"   to FSignature(false, listOf(FParam("address", arrayOf(DataType.UWORD)), FParam("value", arrayOf(DataType.UBYTE))), null),
    "pokew"     to FSignature(false, listOf(FParam("address", arrayOf(DataType.UWORD)), FParam("value", arrayOf(DataType.UWORD))), null),
    "pop"       to FSignature(false, listOf(FParam("target", ByteDatatypes)), null),
    "popw"      to FSignature(false, listOf(FParam("target", WordDatatypes)), null),
    "push"      to FSignature(false, listOf(FParam("value", ByteDatatypes)), null),
    "pushw"     to FSignature(false, listOf(FParam("value", WordDatatypes)), null),
    "rsave"     to FSignature(false, emptyList(), null),
    "rsavex"    to FSignature(false, emptyList(), null),
    "rrestore"  to FSignature(false, emptyList(), null),
    "rrestorex" to FSignature(false, emptyList(), null),
    "memory"    to FSignature(true, listOf(FParam("name", arrayOf(DataType.STR)), FParam("size", arrayOf(DataType.UWORD)), FParam("alignment", arrayOf(DataType.UWORD))), DataType.UWORD),
    "callfar"   to FSignature(false, listOf(FParam("bank", arrayOf(DataType.UBYTE)), FParam("address", arrayOf(DataType.UWORD)), FParam("arg", arrayOf(DataType.UWORD))), DataType.UWORD),
)

val InplaceModifyingBuiltinFunctions = setOf("rol", "ror", "rol2", "ror2", "sort", "reverse")
