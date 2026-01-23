package prog8.code.core

class ReturnConvention(val dt: BaseDataType?, val reg: RegisterOrPair?)
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
                returns.reg == RegisterOrPair.FAC1 -> "floatFAC1"
                returns.reg != null -> returns.reg.toString()
                else -> "<no returnvalue>"
            }
        return "CallConvention[" + paramConvs.joinToString() + " ; returns: $returnConv]"
    }
}

class FParam(val name: String, vararg val possibleDatatypes: BaseDataType)


private val IterableDatatypes = arrayOf(BaseDataType.STR, BaseDataType.ARRAY, BaseDataType.ARRAY_SPLITW)
private val IntegerDatatypes = arrayOf(BaseDataType.UBYTE, BaseDataType.BYTE, BaseDataType.UWORD, BaseDataType.WORD, BaseDataType.LONG)
private val NumericDatatypes = arrayOf(BaseDataType.UBYTE, BaseDataType.BYTE, BaseDataType.UWORD, BaseDataType.WORD, BaseDataType.LONG, BaseDataType.FLOAT)


class FSignature(val pure: Boolean,      // does it have side effects?
                 val returnType: BaseDataType?,
                 vararg val parameters: FParam) {

    fun callConvention(actualParamTypes: List<BaseDataType>): CallConvention {
        val returns: ReturnConvention = when (returnType) {
            BaseDataType.UBYTE, BaseDataType.BYTE -> ReturnConvention(returnType, RegisterOrPair.A)
            BaseDataType.UWORD, BaseDataType.WORD -> ReturnConvention(returnType, RegisterOrPair.AY)
            BaseDataType.LONG -> ReturnConvention(returnType, RegisterOrPair.R14R15)
            BaseDataType.FLOAT -> ReturnConvention(returnType, RegisterOrPair.FAC1)
            in IterableDatatypes -> ReturnConvention(returnType!!, RegisterOrPair.AY)
            null -> ReturnConvention(null, null)
            else -> {
                // return type depends on arg type
                when (val paramType = actualParamTypes.first()) {
                    BaseDataType.UBYTE, BaseDataType.BYTE -> ReturnConvention(paramType, RegisterOrPair.A)
                    BaseDataType.UWORD, BaseDataType.WORD -> ReturnConvention(paramType, RegisterOrPair.AY)
                    BaseDataType.LONG -> ReturnConvention(returnType, RegisterOrPair.R14R15)
                    BaseDataType.FLOAT -> ReturnConvention(paramType, RegisterOrPair.FAC1)
                    in IterableDatatypes -> ReturnConvention(paramType, RegisterOrPair.AY)
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
                    BaseDataType.UBYTE, BaseDataType.BYTE -> ParamConvention(paramType, RegisterOrPair.A, false)
                    BaseDataType.UWORD, BaseDataType.WORD -> ParamConvention(paramType, RegisterOrPair.AY, false)
                    BaseDataType.LONG -> ParamConvention(paramType, RegisterOrPair.R14R15, false)
                    BaseDataType.FLOAT -> ParamConvention(paramType, RegisterOrPair.AY, false)      // NOTE: for builtin functions, floating point arguments are passed by reference (so you get a pointer in AY)
                    in IterableDatatypes -> ParamConvention(paramType, RegisterOrPair.AY, false)
                    else -> ParamConvention(paramType, null, false)
                }
                CallConvention(listOf(paramConv), returns)
            }
            actualParamTypes.size==2 && (actualParamTypes[0].isByte && actualParamTypes[1].isWord) -> {
                TODO("opportunity to pass word+byte arguments in A,Y and X registers but not implemented yet")
            }
            actualParamTypes.size==2 && (actualParamTypes[0].isWord && actualParamTypes[1].isByte) -> {
                TODO("opportunity to pass word+byte arguments in A,Y and X registers but not implemented yet")
            }
            actualParamTypes.size==3 && actualParamTypes.all { it.isByte } -> {
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
    "setlsb"  to FSignature(false, null, FParam("variable", BaseDataType.WORD, BaseDataType.UWORD, BaseDataType.LONG), FParam("value", BaseDataType.BYTE, BaseDataType.UBYTE)),
    "setmsb"  to FSignature(false, null, FParam("variable", BaseDataType.WORD, BaseDataType.UWORD, BaseDataType.LONG), FParam("value", BaseDataType.BYTE, BaseDataType.UBYTE)),
    "rol"     to FSignature(false, null, FParam("item", BaseDataType.UBYTE, BaseDataType.UWORD, BaseDataType.LONG)),
    "ror"     to FSignature(false, null, FParam("item", BaseDataType.UBYTE, BaseDataType.UWORD, BaseDataType.LONG)),
    "rol2"    to FSignature(false, null, FParam("item", BaseDataType.UBYTE, BaseDataType.UWORD, BaseDataType.LONG)),
    "ror2"    to FSignature(false, null, FParam("item", BaseDataType.UBYTE, BaseDataType.UWORD, BaseDataType.LONG)),
    "cmp"     to FSignature(false, null, FParam("value1", *IntegerDatatypes), FParam("value2", *NumericDatatypes)),  // cmp returns result in the cpu status flags, but not asa proper return value
    "prog8_lib_stringcompare"     to FSignature(true, BaseDataType.BYTE, FParam("str1", BaseDataType.STR), FParam("str2", BaseDataType.STR)),
    "prog8_lib_square_byte"       to FSignature(true, BaseDataType.UBYTE, FParam("value", BaseDataType.BYTE, BaseDataType.UBYTE)),
    "prog8_lib_square_word"       to FSignature(true, BaseDataType.UWORD, FParam("value", BaseDataType.WORD, BaseDataType.UWORD)),
    "prog8_lib_structalloc"       to FSignature(true, BaseDataType.UWORD),
    "prog8_lib_copylong"          to FSignature(false, null, FParam("pointer1", BaseDataType.UWORD), FParam("pointer2", BaseDataType.UWORD)),
    "prog8_lib_copyfloat"         to FSignature(false, null, FParam("pointer1", BaseDataType.UWORD), FParam("pointer2", BaseDataType.UWORD)),
    "abs"           to FSignature(true, null, FParam("value", *NumericDatatypes)),
    "abs__byte"     to FSignature(true, BaseDataType.UBYTE, FParam("value", BaseDataType.BYTE)),
    "abs__word"     to FSignature(true, BaseDataType.UWORD, FParam("value", BaseDataType.WORD)),
    "abs__long"     to FSignature(true, BaseDataType.LONG, FParam("value", BaseDataType.LONG)),
    "abs__float"    to FSignature(true, BaseDataType.FLOAT, FParam("value", BaseDataType.FLOAT)),
    "len"           to FSignature(true, BaseDataType.UWORD, FParam("values", *IterableDatatypes)),
    "sizeof"        to FSignature(true, BaseDataType.UBYTE, FParam("object", *(BaseDataType.entries - BaseDataType.STRUCT_INSTANCE).toTypedArray())),
    "offsetof"      to FSignature(true, BaseDataType.UBYTE, FParam("field", BaseDataType.UBYTE)),
    "sgn"           to FSignature(true, BaseDataType.BYTE, FParam("value", *NumericDatatypes)),
    "sqrt"          to FSignature(true, null, FParam("value", *NumericDatatypes)),
    "sqrt__ubyte"   to FSignature(true, BaseDataType.UBYTE, FParam("value", BaseDataType.UBYTE)),
    "sqrt__uword"   to FSignature(true, BaseDataType.UBYTE, FParam("value", BaseDataType.UWORD)),
    "sqrt__long"    to FSignature(true, BaseDataType.UWORD, FParam("value", BaseDataType.LONG)),
    "sqrt__float"   to FSignature(true, BaseDataType.FLOAT, FParam("value", BaseDataType.FLOAT)),
    "swap"          to FSignature(false, null, FParam("var1", *NumericDatatypes + BaseDataType.BOOL), FParam("var2", *NumericDatatypes + BaseDataType.BOOL)),
    "swap__byte"    to FSignature(false, null, FParam("var1", BaseDataType.BYTE, BaseDataType.UBYTE, BaseDataType.BOOL), FParam("var2",BaseDataType.BYTE, BaseDataType.UBYTE, BaseDataType.BOOL)),
    "swap__word"    to FSignature(false, null, FParam("var1", BaseDataType.WORD, BaseDataType.UWORD), FParam("var2",BaseDataType.WORD, BaseDataType.UWORD)),
    "swap__long"    to FSignature(false, null, FParam("var1", BaseDataType.LONG), FParam("var2",BaseDataType.LONG)),
    "swap__float"   to FSignature(false, null, FParam("var1", BaseDataType.FLOAT), FParam("var2",BaseDataType.FLOAT)),
    "divmod"        to FSignature(false, null, FParam("dividend", BaseDataType.UBYTE, BaseDataType.UWORD), FParam("divisor", BaseDataType.UBYTE, BaseDataType.UWORD), FParam("quotient", BaseDataType.UBYTE, BaseDataType.UWORD), FParam("remainder", BaseDataType.UBYTE, BaseDataType.UWORD)),
    "divmod__ubyte" to FSignature(false, null, FParam("dividend", BaseDataType.UBYTE), FParam("divisor", BaseDataType.UBYTE), FParam("quotient", BaseDataType.UBYTE), FParam("remainder", BaseDataType.UBYTE)),
    "divmod__uword" to FSignature(false, null, FParam("dividend", BaseDataType.UWORD), FParam("divisor", BaseDataType.UWORD), FParam("quotient", BaseDataType.UWORD), FParam("remainder", BaseDataType.UWORD)),
    "lsb"           to FSignature(true, BaseDataType.UBYTE, FParam("value", BaseDataType.UWORD, BaseDataType.WORD, BaseDataType.LONG)),
    "lsb__long"     to FSignature(true, BaseDataType.UBYTE, FParam("value", BaseDataType.LONG)),
    "msb"           to FSignature(true, BaseDataType.UBYTE, FParam("value", BaseDataType.UWORD, BaseDataType.WORD, BaseDataType.LONG)),
    "msb__long"     to FSignature(true, BaseDataType.UBYTE, FParam("value", BaseDataType.LONG)),
    "lsw"           to FSignature(true, BaseDataType.UWORD, FParam("value", BaseDataType.UWORD, BaseDataType.WORD, BaseDataType.LONG)),
    "msw"           to FSignature(true, BaseDataType.UWORD, FParam("value", BaseDataType.UWORD, BaseDataType.WORD, BaseDataType.LONG)),
    "mkword"        to FSignature(true, BaseDataType.UWORD, FParam("msb", BaseDataType.UBYTE), FParam("lsb", BaseDataType.UBYTE)),
    "mklong"        to FSignature(true, BaseDataType.LONG, FParam("msb", BaseDataType.UBYTE), FParam("b2", BaseDataType.UBYTE), FParam("b1", BaseDataType.UBYTE), FParam("lsb", BaseDataType.UBYTE)),
    "mklong2"       to FSignature(true, BaseDataType.LONG, FParam("msw", BaseDataType.UWORD), FParam("lsw", BaseDataType.UWORD)),
    "clamp"         to FSignature(true, null, FParam("value", BaseDataType.BYTE), FParam("minimum", BaseDataType.BYTE), FParam("maximum", BaseDataType.BYTE)),
    "clamp__byte"   to FSignature(true, BaseDataType.BYTE, FParam("value", BaseDataType.BYTE), FParam("minimum", BaseDataType.BYTE), FParam("maximum", BaseDataType.BYTE)),
    "clamp__ubyte"  to FSignature(true, BaseDataType.UBYTE, FParam("value", BaseDataType.UBYTE), FParam("minimum", BaseDataType.UBYTE), FParam("maximum", BaseDataType.UBYTE)),
    "clamp__word"   to FSignature(true, BaseDataType.WORD, FParam("value", BaseDataType.WORD), FParam("minimum", BaseDataType.WORD), FParam("maximum", BaseDataType.WORD)),
    "clamp__uword"  to FSignature(true, BaseDataType.UWORD, FParam("value", BaseDataType.UWORD), FParam("minimum", BaseDataType.UWORD), FParam("maximum", BaseDataType.UWORD)),
    "clamp__long"   to FSignature(true, BaseDataType.LONG, FParam("value", BaseDataType.LONG), FParam("minimum", BaseDataType.LONG), FParam("maximum", BaseDataType.LONG)),
    "min"           to FSignature(true, null, FParam("val1", BaseDataType.BYTE), FParam("val2", BaseDataType.BYTE)),
    "min__byte"     to FSignature(true, BaseDataType.BYTE, FParam("val1", BaseDataType.BYTE), FParam("val2", BaseDataType.BYTE)),
    "min__ubyte"    to FSignature(true, BaseDataType.UBYTE, FParam("val1", BaseDataType.UBYTE), FParam("val2", BaseDataType.UBYTE)),
    "min__word"     to FSignature(true, BaseDataType.WORD, FParam("val1", BaseDataType.WORD), FParam("val2", BaseDataType.WORD)),
    "min__uword"    to FSignature(true, BaseDataType.UWORD, FParam("val1", BaseDataType.UWORD), FParam("val2", BaseDataType.UWORD)),
    "min__long"     to FSignature(true, BaseDataType.LONG, FParam("val1", BaseDataType.LONG), FParam("val2", BaseDataType.LONG)),
    "max"           to FSignature(true, null, FParam("val1", BaseDataType.BYTE), FParam("val2", BaseDataType.BYTE)),
    "max__byte"     to FSignature(true, BaseDataType.BYTE, FParam("val1", BaseDataType.BYTE), FParam("val2", BaseDataType.BYTE)),
    "max__ubyte"    to FSignature(true, BaseDataType.UBYTE, FParam("val1", BaseDataType.UBYTE), FParam("val2", BaseDataType.UBYTE)),
    "max__word"     to FSignature(true, BaseDataType.WORD, FParam("val1", BaseDataType.WORD), FParam("val2", BaseDataType.WORD)),
    "max__uword"    to FSignature(true, BaseDataType.UWORD, FParam("val1", BaseDataType.UWORD), FParam("val2", BaseDataType.UWORD)),
    "max__long"     to FSignature(true, BaseDataType.LONG, FParam("val1", BaseDataType.LONG), FParam("val2", BaseDataType.LONG)),
    "peek"          to FSignature(true, BaseDataType.UBYTE, FParam("address", BaseDataType.UWORD)),
    "peekbool"      to FSignature(true, BaseDataType.BOOL, FParam("address", BaseDataType.UWORD)),
    "peekw"         to FSignature(true, BaseDataType.UWORD, FParam("address", BaseDataType.UWORD)),
    "peekl"         to FSignature(true, BaseDataType.LONG, FParam("address", BaseDataType.UWORD)),
    "peekf"         to FSignature(true, BaseDataType.FLOAT, FParam("address", BaseDataType.UWORD)),
    "poke"          to FSignature(false, null, FParam("address", BaseDataType.UWORD), FParam("value", BaseDataType.UBYTE, BaseDataType.BYTE)),
    "pokebool"      to FSignature(false, null, FParam("address", BaseDataType.UWORD), FParam("value", BaseDataType.BOOL)),
    "pokebowl"      to FSignature(false, null, FParam("address", BaseDataType.UWORD), FParam("value", BaseDataType.BOOL)),
    "pokew"         to FSignature(false, null, FParam("address", BaseDataType.UWORD), FParam("value", BaseDataType.UWORD, BaseDataType.WORD)),
    "pokel"         to FSignature(false, null, FParam("address", BaseDataType.UWORD), FParam("value", BaseDataType.LONG)),
    "pokef"         to FSignature(false, null, FParam("address", BaseDataType.UWORD), FParam("value", BaseDataType.FLOAT)),
    "pokemon"       to FSignature(false, BaseDataType.UBYTE, FParam("address", BaseDataType.UWORD), FParam("value", BaseDataType.UBYTE)),
    "rsave"         to FSignature(false, null),
    "rrestore"      to FSignature(false, null),
    "memory"        to FSignature(true, BaseDataType.UWORD, FParam("name", BaseDataType.STR), FParam("size", BaseDataType.UWORD), FParam("alignment", BaseDataType.UWORD)),
    "callfar"       to FSignature(false, BaseDataType.UWORD, FParam("bank", BaseDataType.UBYTE), FParam("address", BaseDataType.UWORD), FParam("arg", BaseDataType.UWORD)),
    "callfar2"      to FSignature(false, BaseDataType.UWORD, FParam("bank", BaseDataType.UBYTE), FParam("address", BaseDataType.UWORD), FParam("argA", BaseDataType.UBYTE), FParam("argX", BaseDataType.UBYTE), FParam("argY", BaseDataType.UBYTE), FParam("argC", BaseDataType.BOOL)),
    "call"          to FSignature(false, BaseDataType.UWORD, FParam("address", BaseDataType.UWORD)),
)

val InplaceModifyingBuiltinFunctions = setOf(
    "setlsb", "setmsb",
    "rol", "ror", "rol2", "ror2",
    "divmod", "divmod__ubyte", "divmod__uword",
    "swap", "swap__byte", "swap__word", "swap__long", "swap__float"
)

val SimpleBuiltinFunctions = setOf(
    "msb", "lsb", "msw", "lsw",
    "mkword", "mklong", "mklong2",
    "set_carry", "set_irqd", "clear_carry", "clear_irqd")