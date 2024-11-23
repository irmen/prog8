package prog8.code.core

class ReturnConvention(val dt: DataType?, val reg: RegisterOrPair?)
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
                returns.reg == RegisterOrPair.FAC1 -> "floatFAC1"
                returns.reg != null -> returns.reg.toString()
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
            DataType.UBYTE, DataType.BYTE -> ReturnConvention(returnType, RegisterOrPair.A)
            DataType.UWORD, DataType.WORD -> ReturnConvention(returnType, RegisterOrPair.AY)
            DataType.FLOAT -> ReturnConvention(returnType, RegisterOrPair.FAC1)
            in PassByReferenceDatatypes -> ReturnConvention(returnType!!, RegisterOrPair.AY)
            null -> ReturnConvention(null, null)
            else -> {
                // return type depends on arg type
                when (val paramType = actualParamTypes.first()) {
                    DataType.UBYTE, DataType.BYTE -> ReturnConvention(paramType, RegisterOrPair.A)
                    DataType.UWORD, DataType.WORD -> ReturnConvention(paramType, RegisterOrPair.AY)
                    DataType.FLOAT -> ReturnConvention(paramType, RegisterOrPair.FAC1)
                    in PassByReferenceDatatypes -> ReturnConvention(paramType, RegisterOrPair.AY)
                    else -> ReturnConvention(paramType, null)
                }
            }
        }

        return when {
            actualParamTypes.isEmpty() -> CallConvention(emptyList(), returns)
            actualParamTypes.size==1 -> {
                // One parameter goes via register/registerpair.
                // this avoids repeated code for every caller to store the value in the subroutine's argument variable.
                // (that store is still done, but only coded once at the start at the subroutine itself rather than at every call site).
                val paramConv = when(val paramType = actualParamTypes[0]) {
                    DataType.UBYTE, DataType.BYTE -> ParamConvention(paramType, RegisterOrPair.A, false)
                    DataType.UWORD, DataType.WORD -> ParamConvention(paramType, RegisterOrPair.AY, false)
                    DataType.FLOAT -> ParamConvention(paramType, RegisterOrPair.AY, false)      // NOTE: for builtin functions, floating point arguments are passed by reference (so you get a pointer in AY)
                    in PassByReferenceDatatypes -> ParamConvention(paramType, RegisterOrPair.AY, false)
                    else -> ParamConvention(paramType, null, false)
                }
                CallConvention(listOf(paramConv), returns)
            }
            actualParamTypes.size==2 && (actualParamTypes[0] in ByteDatatypes && actualParamTypes[1] in WordDatatypes) -> {
                TODO("opportunity to pass word+byte arguments in A,Y and X registers but not implemented yet")
            }
            actualParamTypes.size==2 && (actualParamTypes[0] in WordDatatypes && actualParamTypes[1] in ByteDatatypes) -> {
                TODO("opportunity to pass word+byte arguments in A,Y and X registers but not implemented yet")
            }
            actualParamTypes.size==3 && actualParamTypes.all { it in ByteDatatypes } -> {
                TODO("opportunity to pass 3 byte arguments in A,Y and X registers but not implemented yet")
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
    "setlsb"    to FSignature(false, listOf(FParam("variable", arrayOf(DataType.WORD, DataType.UWORD)), FParam("value", arrayOf(DataType.BYTE, DataType.UBYTE))), null),
    "setmsb"    to FSignature(false, listOf(FParam("variable", arrayOf(DataType.WORD, DataType.UWORD)), FParam("value", arrayOf(DataType.BYTE, DataType.UBYTE))), null),
    "rol"       to FSignature(false, listOf(FParam("item", arrayOf(DataType.UBYTE, DataType.UWORD))), null),
    "ror"       to FSignature(false, listOf(FParam("item", arrayOf(DataType.UBYTE, DataType.UWORD))), null),
    "rol2"      to FSignature(false, listOf(FParam("item", arrayOf(DataType.UBYTE, DataType.UWORD))), null),
    "ror2"      to FSignature(false, listOf(FParam("item", arrayOf(DataType.UBYTE, DataType.UWORD))), null),
    // cmp returns a status in the carry flag, but not a proper return value
    "cmp"       to FSignature(false, listOf(FParam("value1", IntegerDatatypes), FParam("value2", NumericDatatypes)), null),
    "prog8_lib_stringcompare"     to FSignature(true, listOf(FParam("str1", arrayOf(DataType.STR)), FParam("str2", arrayOf(DataType.STR))), DataType.BYTE),
    "prog8_lib_square_byte"       to FSignature(true, listOf(FParam("value", arrayOf(DataType.BYTE, DataType.UBYTE))), DataType.UBYTE),
    "prog8_lib_square_word"       to FSignature(true, listOf(FParam("value", arrayOf(DataType.WORD, DataType.UWORD))), DataType.UWORD),
    "prog8_ifelse_bittest_set"    to FSignature(true, listOf(FParam("variable", ByteDatatypes), FParam("bitnumber", arrayOf(DataType.UBYTE))), DataType.BOOL),
    "prog8_ifelse_bittest_notset" to FSignature(true, listOf(FParam("variable", ByteDatatypes), FParam("bitnumber", arrayOf(DataType.UBYTE))), DataType.BOOL),
    "abs"       to FSignature(true, listOf(FParam("value", NumericDatatypes)), null),
    "abs__byte"  to FSignature(true, listOf(FParam("value", arrayOf(DataType.BYTE))), DataType.BYTE),
    "abs__word"  to FSignature(true, listOf(FParam("value", arrayOf(DataType.WORD))), DataType.WORD),
    "abs__float" to FSignature(true, listOf(FParam("value", arrayOf(DataType.FLOAT))), DataType.FLOAT),
    "len"       to FSignature(true, listOf(FParam("values", IterableDatatypes)), DataType.UWORD),
    // normal functions follow:
    "sizeof"    to FSignature(true, listOf(FParam("object", DataType.entries.toTypedArray())), DataType.UBYTE),
    "sgn"       to FSignature(true, listOf(FParam("value", NumericDatatypes)), DataType.BYTE),
    "sqrt"        to FSignature(true, listOf(FParam("value", NumericDatatypes)), null),
    "sqrt__ubyte" to FSignature(true, listOf(FParam("value", arrayOf(DataType.UBYTE))), DataType.UBYTE),
    "sqrt__uword" to FSignature(true, listOf(FParam("value", arrayOf(DataType.UWORD))), DataType.UBYTE),
    "sqrt__float" to FSignature(true, listOf(FParam("value", arrayOf(DataType.FLOAT))), DataType.FLOAT),
    "divmod"           to FSignature(false, listOf(FParam("dividend", arrayOf(DataType.UBYTE, DataType.UWORD)), FParam("divisor", arrayOf(DataType.UBYTE, DataType.UWORD)), FParam("quotient", arrayOf(DataType.UBYTE, DataType.UWORD)), FParam("remainder", arrayOf(DataType.UBYTE, DataType.UWORD))), null),
    "divmod__ubyte"    to FSignature(false, listOf(FParam("dividend", arrayOf(DataType.UBYTE)), FParam("divisor", arrayOf(DataType.UBYTE)), FParam("quotient", arrayOf(DataType.UBYTE)), FParam("remainder", arrayOf(DataType.UBYTE))), null),
    "divmod__uword"    to FSignature(false, listOf(FParam("dividend", arrayOf(DataType.UWORD)), FParam("divisor", arrayOf(DataType.UWORD)), FParam("quotient", arrayOf(DataType.UWORD)), FParam("remainder", arrayOf(DataType.UWORD))), null),
    "lsb"       to FSignature(true, listOf(FParam("value", arrayOf(DataType.UWORD, DataType.WORD, DataType.LONG))), DataType.UBYTE),
    "msb"       to FSignature(true, listOf(FParam("value", arrayOf(DataType.UWORD, DataType.WORD, DataType.LONG))), DataType.UBYTE),
    "bankof"    to FSignature(true, listOf(FParam("value", arrayOf(DataType.UWORD, DataType.WORD, DataType.LONG))), DataType.UBYTE),
    "mkword"    to FSignature(true, listOf(FParam("msb", arrayOf(DataType.UBYTE)), FParam("lsb", arrayOf(DataType.UBYTE))), DataType.UWORD),
    "clamp"     to FSignature(true, listOf(FParam("value", arrayOf(DataType.BYTE)), FParam("minimum", arrayOf(DataType.BYTE)), FParam("maximum", arrayOf(DataType.BYTE))), null),
    "clamp__byte"  to FSignature(true, listOf(FParam("value", arrayOf(DataType.BYTE)), FParam("minimum", arrayOf(DataType.BYTE)), FParam("maximum", arrayOf(DataType.BYTE))), DataType.BYTE),
    "clamp__ubyte" to FSignature(true, listOf(FParam("value", arrayOf(DataType.UBYTE)), FParam("minimum", arrayOf(DataType.UBYTE)), FParam("maximum", arrayOf(DataType.UBYTE))), DataType.UBYTE),
    "clamp__word"  to FSignature(true, listOf(FParam("value", arrayOf(DataType.WORD)), FParam("minimum", arrayOf(DataType.WORD)), FParam("maximum", arrayOf(DataType.WORD))), DataType.WORD),
    "clamp__uword" to FSignature(true, listOf(FParam("value", arrayOf(DataType.UWORD)), FParam("minimum", arrayOf(DataType.UWORD)), FParam("maximum", arrayOf(DataType.UWORD))), DataType.UWORD),
    "min"        to FSignature(true, listOf(FParam("val1", arrayOf(DataType.BYTE)), FParam("val2", arrayOf(DataType.BYTE))), null),
    "min__byte"  to FSignature(true, listOf(FParam("val1", arrayOf(DataType.BYTE)), FParam("val2", arrayOf(DataType.BYTE))), DataType.BYTE),
    "min__ubyte" to FSignature(true, listOf(FParam("val1", arrayOf(DataType.UBYTE)), FParam("val2", arrayOf(DataType.UBYTE))), DataType.UBYTE),
    "min__word"  to FSignature(true, listOf(FParam("val1", arrayOf(DataType.WORD)), FParam("val2", arrayOf(DataType.WORD))), DataType.WORD),
    "min__uword" to FSignature(true, listOf(FParam("val1", arrayOf(DataType.UWORD)), FParam("val2", arrayOf(DataType.UWORD))), DataType.UWORD),
    "max"        to FSignature(true, listOf(FParam("val1", arrayOf(DataType.BYTE)), FParam("val2", arrayOf(DataType.BYTE))), null),
    "max__byte"  to FSignature(true, listOf(FParam("val1", arrayOf(DataType.BYTE)), FParam("val2", arrayOf(DataType.BYTE))), DataType.BYTE),
    "max__ubyte" to FSignature(true, listOf(FParam("val1", arrayOf(DataType.UBYTE)), FParam("val2", arrayOf(DataType.UBYTE))), DataType.UBYTE),
    "max__word"  to FSignature(true, listOf(FParam("val1", arrayOf(DataType.WORD)), FParam("val2", arrayOf(DataType.WORD))), DataType.WORD),
    "max__uword" to FSignature(true, listOf(FParam("val1", arrayOf(DataType.UWORD)), FParam("val2", arrayOf(DataType.UWORD))), DataType.UWORD),
    "peek"      to FSignature(true, listOf(FParam("address", arrayOf(DataType.UWORD))), DataType.UBYTE),
    "peekw"     to FSignature(true, listOf(FParam("address", arrayOf(DataType.UWORD))), DataType.UWORD),
    "peekf"     to FSignature(true, listOf(FParam("address", arrayOf(DataType.UWORD))), DataType.FLOAT),
    "poke"      to FSignature(false, listOf(FParam("address", arrayOf(DataType.UWORD)), FParam("value", arrayOf(DataType.UBYTE))), null),
    "pokew"     to FSignature(false, listOf(FParam("address", arrayOf(DataType.UWORD)), FParam("value", arrayOf(DataType.UWORD))), null),
    "pokef"     to FSignature(false, listOf(FParam("address", arrayOf(DataType.UWORD)), FParam("value", arrayOf(DataType.FLOAT))), null),
    "pokemon"   to FSignature(false, listOf(FParam("address", arrayOf(DataType.UWORD)), FParam("value", arrayOf(DataType.UBYTE))), DataType.UBYTE),
    "rsave"     to FSignature(false, emptyList(), null),
    "rrestore"  to FSignature(false, emptyList(), null),
    "memory"    to FSignature(true, listOf(FParam("name", arrayOf(DataType.STR)), FParam("size", arrayOf(DataType.UWORD)), FParam("alignment", arrayOf(DataType.UWORD))), DataType.UWORD),
    "callfar"   to FSignature(false, listOf(FParam("bank", arrayOf(DataType.UBYTE)), FParam("address", arrayOf(DataType.UWORD)), FParam("arg", arrayOf(DataType.UWORD))), DataType.UWORD),
    "callfar2"  to FSignature(false, listOf(FParam("bank", arrayOf(DataType.UBYTE)), FParam("address", arrayOf(DataType.UWORD)), FParam("argA", arrayOf(DataType.UBYTE)), FParam("argX", arrayOf(DataType.UBYTE)), FParam("argY", arrayOf(DataType.UBYTE)), FParam("argC", arrayOf(DataType.BOOL))), DataType.UWORD),
    "call"      to FSignature(false, listOf(FParam("address", arrayOf(DataType.UWORD))), DataType.UWORD),
)

val InplaceModifyingBuiltinFunctions = setOf(
    "setlsb", "setmsb",
    "rol", "ror", "rol2", "ror2",
    "divmod", "divmod__ubyte", "divmod__uword"
)
