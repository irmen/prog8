package prog8.code.core

class ReturnConvention(val dt: BaseDataType?, val reg: RegisterOrPair?, val floatFac1: Boolean)
class ParamConvention(val dt: BaseDataType, val reg: RegisterOrPair?, val variable: Boolean)
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

class FParam(val name: String, val possibleDatatypes: Array<BaseDataType>)


private val IterableDatatypes = arrayOf(BaseDataType.STR, BaseDataType.ARRAY, BaseDataType.ARRAY_SPLITW)
private val IntegerDatatypes = arrayOf(BaseDataType.UBYTE, BaseDataType.BYTE, BaseDataType.UWORD, BaseDataType.WORD, BaseDataType.LONG)
private val NumericDatatypes = arrayOf(BaseDataType.UBYTE, BaseDataType.BYTE, BaseDataType.UWORD, BaseDataType.WORD, BaseDataType.LONG, BaseDataType.FLOAT)
private val ByteDatatypes = arrayOf(BaseDataType.UBYTE, BaseDataType.BYTE)
private val ArrayDatatypes = arrayOf(BaseDataType.ARRAY, BaseDataType.ARRAY_SPLITW)


class FSignature(val pure: Boolean,      // does it have side effects?
                      val parameters: List<FParam>,
                      val returnType: BaseDataType?) {

    fun callConvention(actualParamTypes: List<BaseDataType>): CallConvention {
        val returns: ReturnConvention = when (returnType) {
            BaseDataType.UBYTE, BaseDataType.BYTE -> ReturnConvention(returnType, RegisterOrPair.A, false)
            BaseDataType.UWORD, BaseDataType.WORD -> ReturnConvention(returnType, RegisterOrPair.AY, false)
            BaseDataType.FLOAT -> ReturnConvention(returnType, null, true)
            in IterableDatatypes -> ReturnConvention(returnType!!, RegisterOrPair.AY, false)    // pass by ref
            null -> ReturnConvention(null, null, false)
            else -> {
                // return type depends on arg type
                when (val paramType = actualParamTypes.first()) {
                    BaseDataType.UBYTE, BaseDataType.BYTE -> ReturnConvention(paramType, RegisterOrPair.A, false)
                    BaseDataType.UWORD, BaseDataType.WORD -> ReturnConvention(paramType, RegisterOrPair.AY, false)
                    BaseDataType.FLOAT -> ReturnConvention(paramType, null, true)
                    in IterableDatatypes -> ReturnConvention(paramType, RegisterOrPair.AY, false)   // pass by ref
                    else -> ReturnConvention(paramType, null, false)
                }
            }
        }

        return when {
            actualParamTypes.isEmpty() -> CallConvention(emptyList(), returns)
            actualParamTypes.size==1 -> {
                // one parameter goes via register/registerpair
                val paramConv = when(val paramType = actualParamTypes[0]) {
                    BaseDataType.UBYTE, BaseDataType.BYTE -> ParamConvention(paramType, RegisterOrPair.A, false)
                    BaseDataType.UWORD, BaseDataType.WORD -> ParamConvention(paramType, RegisterOrPair.AY, false)
                    BaseDataType.FLOAT -> ParamConvention(paramType, RegisterOrPair.AY, false)
                    in IterableDatatypes -> ParamConvention(paramType, RegisterOrPair.AY, false)    // pass by ref
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
    "setlsb"    to FSignature(false, listOf(FParam("variable", arrayOf(BaseDataType.WORD, BaseDataType.UWORD)), FParam("value", arrayOf(BaseDataType.BYTE, BaseDataType.UBYTE))), null),
    "setmsb"    to FSignature(false, listOf(FParam("variable", arrayOf(BaseDataType.WORD, BaseDataType.UWORD)), FParam("value", arrayOf(BaseDataType.BYTE, BaseDataType.UBYTE))), null),
    "rol"       to FSignature(false, listOf(FParam("item", arrayOf(BaseDataType.UBYTE, BaseDataType.UWORD))), null),
    "ror"       to FSignature(false, listOf(FParam("item", arrayOf(BaseDataType.UBYTE, BaseDataType.UWORD))), null),
    "rol2"      to FSignature(false, listOf(FParam("item", arrayOf(BaseDataType.UBYTE, BaseDataType.UWORD))), null),
    "ror2"      to FSignature(false, listOf(FParam("item", arrayOf(BaseDataType.UBYTE, BaseDataType.UWORD))), null),
    // cmp returns a status in the carry flag, but not a proper return value
    "cmp"       to FSignature(false, listOf(FParam("value1", IntegerDatatypes), FParam("value2", NumericDatatypes)), null),
    "prog8_lib_stringcompare"     to FSignature(true, listOf(FParam("str1", arrayOf(BaseDataType.STR)), FParam("str2", arrayOf(BaseDataType.STR))), BaseDataType.BYTE),
    "prog8_lib_arraycopy"         to FSignature(false, listOf(FParam("source", ArrayDatatypes), FParam("target", ArrayDatatypes)), null),
    "prog8_lib_square_byte"       to FSignature(true, listOf(FParam("value", arrayOf(BaseDataType.BYTE, BaseDataType.UBYTE))), BaseDataType.UBYTE),
    "prog8_lib_square_word"       to FSignature(true, listOf(FParam("value", arrayOf(BaseDataType.WORD, BaseDataType.UWORD))), BaseDataType.UWORD),
    "prog8_ifelse_bittest_set"    to FSignature(true, listOf(FParam("variable", ByteDatatypes), FParam("bitnumber", arrayOf(BaseDataType.UBYTE))), BaseDataType.BOOL),
    "prog8_ifelse_bittest_notset" to FSignature(true, listOf(FParam("variable", ByteDatatypes), FParam("bitnumber", arrayOf(BaseDataType.UBYTE))), BaseDataType.BOOL),
    "abs"       to FSignature(true, listOf(FParam("value", NumericDatatypes)), null),
    "abs__byte"  to FSignature(true, listOf(FParam("value", arrayOf(BaseDataType.BYTE))), BaseDataType.BYTE),
    "abs__word"  to FSignature(true, listOf(FParam("value", arrayOf(BaseDataType.WORD))), BaseDataType.WORD),
    "abs__float" to FSignature(true, listOf(FParam("value", arrayOf(BaseDataType.FLOAT))), BaseDataType.FLOAT),
    "len"       to FSignature(true, listOf(FParam("values", IterableDatatypes)), BaseDataType.UWORD),
    // normal functions follow:
    "sizeof"    to FSignature(true, listOf(FParam("object", BaseDataType.entries.toTypedArray())), BaseDataType.UBYTE),
    "sgn"       to FSignature(true, listOf(FParam("value", NumericDatatypes)), BaseDataType.BYTE),
    "sqrt"        to FSignature(true, listOf(FParam("value", NumericDatatypes)), null),
    "sqrt__ubyte" to FSignature(true, listOf(FParam("value", arrayOf(BaseDataType.UBYTE))), BaseDataType.UBYTE),
    "sqrt__uword" to FSignature(true, listOf(FParam("value", arrayOf(BaseDataType.UWORD))), BaseDataType.UBYTE),
    "sqrt__float" to FSignature(true, listOf(FParam("value", arrayOf(BaseDataType.FLOAT))), BaseDataType.FLOAT),
    "divmod"           to FSignature(false, listOf(FParam("dividend", arrayOf(BaseDataType.UBYTE, BaseDataType.UWORD)), FParam("divisor", arrayOf(BaseDataType.UBYTE, BaseDataType.UWORD)), FParam("quotient", arrayOf(BaseDataType.UBYTE, BaseDataType.UWORD)), FParam("remainder", arrayOf(BaseDataType.UBYTE, BaseDataType.UWORD))), null),
    "divmod__ubyte"    to FSignature(false, listOf(FParam("dividend", arrayOf(BaseDataType.UBYTE)), FParam("divisor", arrayOf(BaseDataType.UBYTE)), FParam("quotient", arrayOf(BaseDataType.UBYTE)), FParam("remainder", arrayOf(BaseDataType.UBYTE))), null),
    "divmod__uword"    to FSignature(false, listOf(FParam("dividend", arrayOf(BaseDataType.UWORD)), FParam("divisor", arrayOf(BaseDataType.UWORD)), FParam("quotient", arrayOf(BaseDataType.UWORD)), FParam("remainder", arrayOf(BaseDataType.UWORD))), null),
    "lsb"       to FSignature(true, listOf(FParam("value", arrayOf(BaseDataType.UWORD, BaseDataType.WORD))), BaseDataType.UBYTE),
    "msb"       to FSignature(true, listOf(FParam("value", arrayOf(BaseDataType.UWORD, BaseDataType.WORD))), BaseDataType.UBYTE),
    "mkword"    to FSignature(true, listOf(FParam("msb", arrayOf(BaseDataType.UBYTE)), FParam("lsb", arrayOf(BaseDataType.UBYTE))), BaseDataType.UWORD),
    "clamp"     to FSignature(true, listOf(FParam("value", arrayOf(BaseDataType.BYTE)), FParam("minimum", arrayOf(BaseDataType.BYTE)), FParam("maximum", arrayOf(BaseDataType.BYTE))), null),
    "clamp__byte"  to FSignature(true, listOf(FParam("value", arrayOf(BaseDataType.BYTE)), FParam("minimum", arrayOf(BaseDataType.BYTE)), FParam("maximum", arrayOf(BaseDataType.BYTE))), BaseDataType.BYTE),
    "clamp__ubyte" to FSignature(true, listOf(FParam("value", arrayOf(BaseDataType.UBYTE)), FParam("minimum", arrayOf(BaseDataType.UBYTE)), FParam("maximum", arrayOf(BaseDataType.UBYTE))), BaseDataType.UBYTE),
    "clamp__word"  to FSignature(true, listOf(FParam("value", arrayOf(BaseDataType.WORD)), FParam("minimum", arrayOf(BaseDataType.WORD)), FParam("maximum", arrayOf(BaseDataType.WORD))), BaseDataType.WORD),
    "clamp__uword" to FSignature(true, listOf(FParam("value", arrayOf(BaseDataType.UWORD)), FParam("minimum", arrayOf(BaseDataType.UWORD)), FParam("maximum", arrayOf(BaseDataType.UWORD))), BaseDataType.UWORD),
    "min"        to FSignature(true, listOf(FParam("val1", arrayOf(BaseDataType.BYTE)), FParam("val2", arrayOf(BaseDataType.BYTE))), null),
    "min__byte"  to FSignature(true, listOf(FParam("val1", arrayOf(BaseDataType.BYTE)), FParam("val2", arrayOf(BaseDataType.BYTE))), BaseDataType.BYTE),
    "min__ubyte" to FSignature(true, listOf(FParam("val1", arrayOf(BaseDataType.UBYTE)), FParam("val2", arrayOf(BaseDataType.UBYTE))), BaseDataType.UBYTE),
    "min__word"  to FSignature(true, listOf(FParam("val1", arrayOf(BaseDataType.WORD)), FParam("val2", arrayOf(BaseDataType.WORD))), BaseDataType.WORD),
    "min__uword" to FSignature(true, listOf(FParam("val1", arrayOf(BaseDataType.UWORD)), FParam("val2", arrayOf(BaseDataType.UWORD))), BaseDataType.UWORD),
    "max"        to FSignature(true, listOf(FParam("val1", arrayOf(BaseDataType.BYTE)), FParam("val2", arrayOf(BaseDataType.BYTE))), null),
    "max__byte"  to FSignature(true, listOf(FParam("val1", arrayOf(BaseDataType.BYTE)), FParam("val2", arrayOf(BaseDataType.BYTE))), BaseDataType.BYTE),
    "max__ubyte" to FSignature(true, listOf(FParam("val1", arrayOf(BaseDataType.UBYTE)), FParam("val2", arrayOf(BaseDataType.UBYTE))), BaseDataType.UBYTE),
    "max__word"  to FSignature(true, listOf(FParam("val1", arrayOf(BaseDataType.WORD)), FParam("val2", arrayOf(BaseDataType.WORD))), BaseDataType.WORD),
    "max__uword" to FSignature(true, listOf(FParam("val1", arrayOf(BaseDataType.UWORD)), FParam("val2", arrayOf(BaseDataType.UWORD))), BaseDataType.UWORD),
    "peek"      to FSignature(true, listOf(FParam("address", arrayOf(BaseDataType.UWORD))), BaseDataType.UBYTE),
    "peekw"     to FSignature(true, listOf(FParam("address", arrayOf(BaseDataType.UWORD))), BaseDataType.UWORD),
    "peekf"     to FSignature(true, listOf(FParam("address", arrayOf(BaseDataType.UWORD))), BaseDataType.FLOAT),
    "poke"      to FSignature(false, listOf(FParam("address", arrayOf(BaseDataType.UWORD)), FParam("value", arrayOf(BaseDataType.UBYTE))), null),
    "pokew"     to FSignature(false, listOf(FParam("address", arrayOf(BaseDataType.UWORD)), FParam("value", arrayOf(BaseDataType.UWORD))), null),
    "pokef"     to FSignature(false, listOf(FParam("address", arrayOf(BaseDataType.UWORD)), FParam("value", arrayOf(BaseDataType.FLOAT))), null),
    "pokemon"   to FSignature(false, listOf(FParam("address", arrayOf(BaseDataType.UWORD)), FParam("value", arrayOf(BaseDataType.UBYTE))), BaseDataType.UBYTE),
    "rsave"     to FSignature(false, emptyList(), null),
    "rrestore"  to FSignature(false, emptyList(), null),
    "memory"    to FSignature(true, listOf(FParam("name", arrayOf(BaseDataType.STR)), FParam("size", arrayOf(BaseDataType.UWORD)), FParam("alignment", arrayOf(BaseDataType.UWORD))), BaseDataType.UWORD),
    "callfar"   to FSignature(false, listOf(FParam("bank", arrayOf(BaseDataType.UBYTE)), FParam("address", arrayOf(BaseDataType.UWORD)), FParam("arg", arrayOf(BaseDataType.UWORD))), BaseDataType.UWORD),
    "callfar2"  to FSignature(false, listOf(FParam("bank", arrayOf(BaseDataType.UBYTE)), FParam("address", arrayOf(BaseDataType.UWORD)), FParam("argA", arrayOf(BaseDataType.UBYTE)), FParam("argX", arrayOf(BaseDataType.UBYTE)), FParam("argY", arrayOf(BaseDataType.UBYTE)), FParam("argC", arrayOf(BaseDataType.BOOL))), BaseDataType.UWORD),
    "call"      to FSignature(false, listOf(FParam("address", arrayOf(BaseDataType.UWORD))), BaseDataType.UWORD),
)

val InplaceModifyingBuiltinFunctions = setOf(
    "setlsb", "setmsb",
    "rol", "ror", "rol2", "ror2",
    "divmod", "divmod__ubyte", "divmod__uword"
)
