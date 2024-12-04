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

class FParam(val name: String, vararg val possibleDatatypes: DataType)

class FSignature(val pure: Boolean,      // does it have side effects?
                 val returnType: DataType?,
                 vararg val parameters: FParam) {

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
    "setlsb"  to FSignature(false, null, FParam("variable", DataType.WORD, DataType.UWORD), FParam("value", DataType.BYTE, DataType.UBYTE)),
    "setmsb"  to FSignature(false, null, FParam("variable", DataType.WORD, DataType.UWORD), FParam("value", DataType.BYTE, DataType.UBYTE)),
    "rol"     to FSignature(false, null, FParam("item", DataType.UBYTE, DataType.UWORD)),
    "ror"     to FSignature(false, null, FParam("item", DataType.UBYTE, DataType.UWORD)),
    "rol2"    to FSignature(false, null, FParam("item", DataType.UBYTE, DataType.UWORD)),
    "ror2"    to FSignature(false, null, FParam("item", DataType.UBYTE, DataType.UWORD)),
    "cmp"     to FSignature(false, null, FParam("value1", *IntegerDatatypes), FParam("value2", *NumericDatatypes)),  // cmp returns a status in the carry flag, but not a proper return value
    "prog8_lib_stringcompare"     to FSignature(true, DataType.BYTE, FParam("str1", DataType.STR), FParam("str2", DataType.STR)),
    "prog8_lib_square_byte"       to FSignature(true, DataType.UBYTE, FParam("value", DataType.BYTE, DataType.UBYTE)),
    "prog8_lib_square_word"       to FSignature(true, DataType.UWORD, FParam("value", DataType.WORD, DataType.UWORD)),
    "prog8_ifelse_bittest_set"    to FSignature(true, DataType.BOOL, FParam("variable", *ByteDatatypes), FParam("bitnumber", DataType.UBYTE)),
    "prog8_ifelse_bittest_notset" to FSignature(true, DataType.BOOL, FParam("variable", *ByteDatatypes), FParam("bitnumber", DataType.UBYTE)),
    "abs"           to FSignature(true, null, FParam("value", *NumericDatatypes)),
    "abs__byte"     to FSignature(true, DataType.BYTE, FParam("value", DataType.BYTE)),
    "abs__word"     to FSignature(true, DataType.WORD, FParam("value", DataType.WORD)),
    "abs__float"    to FSignature(true, DataType.FLOAT, FParam("value", DataType.FLOAT)),
    "len"           to FSignature(true, DataType.UWORD, FParam("values", *IterableDatatypes)),
    "sizeof"        to FSignature(true, DataType.UBYTE, FParam("object", *DataType.entries.toTypedArray())),
    "sgn"           to FSignature(true, DataType.BYTE, FParam("value", *NumericDatatypes)),
    "sqrt"          to FSignature(true, null, FParam("value", *NumericDatatypes)),
    "sqrt__ubyte"   to FSignature(true, DataType.UBYTE, FParam("value", DataType.UBYTE)),
    "sqrt__uword"   to FSignature(true, DataType.UBYTE, FParam("value", DataType.UWORD)),
    "sqrt__float"   to FSignature(true, DataType.FLOAT, FParam("value", DataType.FLOAT)),
    "divmod"        to FSignature(false, null, FParam("dividend", DataType.UBYTE, DataType.UWORD), FParam("divisor", DataType.UBYTE, DataType.UWORD), FParam("quotient", DataType.UBYTE, DataType.UWORD), FParam("remainder", DataType.UBYTE, DataType.UWORD)),
    "divmod__ubyte" to FSignature(false, null, FParam("dividend", DataType.UBYTE), FParam("divisor", DataType.UBYTE), FParam("quotient", DataType.UBYTE), FParam("remainder", DataType.UBYTE)),
    "divmod__uword" to FSignature(false, null, FParam("dividend", DataType.UWORD), FParam("divisor", DataType.UWORD), FParam("quotient", DataType.UWORD), FParam("remainder", DataType.UWORD)),
    "lsb"           to FSignature(true, DataType.UBYTE, FParam("value", DataType.UWORD, DataType.WORD, DataType.LONG)),
    "lsw"           to FSignature(true, DataType.UWORD, FParam("value", DataType.UWORD, DataType.WORD, DataType.LONG)),
    "msb"           to FSignature(true, DataType.UBYTE, FParam("value", DataType.UWORD, DataType.WORD, DataType.LONG)),
    "msw"           to FSignature(true, DataType.UWORD, FParam("value", DataType.UWORD, DataType.WORD, DataType.LONG)),
    "mkword"        to FSignature(true, DataType.UWORD, FParam("msb", DataType.UBYTE), FParam("lsb", DataType.UBYTE)),
    "clamp"         to FSignature(true, null, FParam("value", DataType.BYTE), FParam("minimum", DataType.BYTE), FParam("maximum", DataType.BYTE)),
    "clamp__byte"   to FSignature(true, DataType.BYTE, FParam("value", DataType.BYTE), FParam("minimum", DataType.BYTE), FParam("maximum", DataType.BYTE)),
    "clamp__ubyte"  to FSignature(true, DataType.UBYTE, FParam("value", DataType.UBYTE), FParam("minimum", DataType.UBYTE), FParam("maximum", DataType.UBYTE)),
    "clamp__word"   to FSignature(true, DataType.WORD, FParam("value", DataType.WORD), FParam("minimum", DataType.WORD), FParam("maximum", DataType.WORD)),
    "clamp__uword"  to FSignature(true, DataType.UWORD, FParam("value", DataType.UWORD), FParam("minimum", DataType.UWORD), FParam("maximum", DataType.UWORD)),
    "min"           to FSignature(true, null, FParam("val1", DataType.BYTE), FParam("val2", DataType.BYTE)),
    "min__byte"     to FSignature(true, DataType.BYTE, FParam("val1", DataType.BYTE), FParam("val2", DataType.BYTE)),
    "min__ubyte"    to FSignature(true, DataType.UBYTE, FParam("val1", DataType.UBYTE), FParam("val2", DataType.UBYTE)),
    "min__word"     to FSignature(true, DataType.WORD, FParam("val1", DataType.WORD), FParam("val2", DataType.WORD)),
    "min__uword"    to FSignature(true, DataType.UWORD, FParam("val1", DataType.UWORD), FParam("val2", DataType.UWORD)),
    "max"           to FSignature(true, null, FParam("val1", DataType.BYTE), FParam("val2", DataType.BYTE)),
    "max__byte"     to FSignature(true, DataType.BYTE, FParam("val1", DataType.BYTE), FParam("val2", DataType.BYTE)),
    "max__ubyte"    to FSignature(true, DataType.UBYTE, FParam("val1", DataType.UBYTE), FParam("val2", DataType.UBYTE)),
    "max__word"     to FSignature(true, DataType.WORD, FParam("val1", DataType.WORD), FParam("val2", DataType.WORD)),
    "max__uword"    to FSignature(true, DataType.UWORD, FParam("val1", DataType.UWORD), FParam("val2", DataType.UWORD)),
    "peek"          to FSignature(true, DataType.UBYTE, FParam("address", DataType.UWORD)),
    "peekw"         to FSignature(true, DataType.UWORD, FParam("address", DataType.UWORD)),
    "peekf"         to FSignature(true, DataType.FLOAT, FParam("address", DataType.UWORD)),
    "poke"          to FSignature(false, null, FParam("address", DataType.UWORD), FParam("value", DataType.UBYTE)),
    "pokew"         to FSignature(false, null, FParam("address", DataType.UWORD), FParam("value", DataType.UWORD)),
    "pokef"         to FSignature(false, null, FParam("address", DataType.UWORD), FParam("value", DataType.FLOAT)),
    "pokemon"       to FSignature(false, DataType.UBYTE, FParam("address", DataType.UWORD), FParam("value", DataType.UBYTE)),
    "rsave"         to FSignature(false, null),
    "rrestore"      to FSignature(false, null),
    "memory"        to FSignature(true, DataType.UWORD, FParam("name", DataType.STR), FParam("size", DataType.UWORD), FParam("alignment", DataType.UWORD)),
    "callfar"       to FSignature(false, DataType.UWORD, FParam("bank", DataType.UBYTE), FParam("address", DataType.UWORD), FParam("arg", DataType.UWORD)),
    "callfar2"      to FSignature(false, DataType.UWORD, FParam("bank", DataType.UBYTE), FParam("address", DataType.UWORD), FParam("argA", DataType.UBYTE), FParam("argX", DataType.UBYTE), FParam("argY", DataType.UBYTE), FParam("argC", DataType.BOOL)),
    "call"          to FSignature(false, DataType.UWORD, FParam("address", DataType.UWORD)),
)

val InplaceModifyingBuiltinFunctions = setOf(
    "setlsb", "setmsb",
    "rol", "ror", "rol2", "ror2",
    "divmod", "divmod__ubyte", "divmod__uword"
)
