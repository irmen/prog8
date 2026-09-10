package prog8.intermediate

import prog8.code.core.*


fun DataType.irTypeString(length: UInt?): String {
    val lengthStr = if(length==0u) "" else length.toString()
    return when (this.base) {
        BaseDataType.BOOL -> "bool"
        BaseDataType.UBYTE -> "ubyte"
        BaseDataType.BYTE -> "byte"
        BaseDataType.UWORD -> "uword"
        BaseDataType.WORD -> "word"
        BaseDataType.LONG -> "long"
        BaseDataType.FLOAT -> "float"
        BaseDataType.STR -> "ubyte[$lengthStr]"             // here string doesn't exist as a separate datatype anymore
        BaseDataType.POINTER -> "pointer"
        BaseDataType.ARRAY_POINTER -> {
            val subTypeName = sub?.name?.lowercase() ?: subType?.scopedNameString
                ?: throw IllegalArgumentException("ARRAY_POINTER missing subtype")
            "^^$subTypeName[$lengthStr]"
        }
        BaseDataType.STRUCT_INSTANCE -> {
            if(sub!=null)
                sub!!.name.lowercase()
            else
                subType!!.scopedNameString
        }
        BaseDataType.ARRAY -> {
            when(this.sub) {
                BaseDataType.UBYTE -> "ubyte[$lengthStr]"
                BaseDataType.UWORD -> "uword[$lengthStr]"
                BaseDataType.BYTE -> "byte[$lengthStr]"
                BaseDataType.WORD -> "word[$lengthStr]"
                BaseDataType.LONG -> "long[$lengthStr]"
                BaseDataType.BOOL -> "bool[$lengthStr]"
                BaseDataType.FLOAT -> "float[$lengthStr]"
                BaseDataType.STRUCT_INSTANCE -> if(subType!=null) "${subType!!.scopedNameString}[$lengthStr]" else "$subTypeFromAntlr[$lengthStr]"
                else -> throw IllegalArgumentException("invalid sub type")
            }
        }
        BaseDataType.ARRAY_SPLITW -> {
            when(this.sub) {
                BaseDataType.UWORD -> "uword[$lengthStr]"       // should be 2 separate byte arrays by now really?
                BaseDataType.WORD -> "word[$lengthStr]"          // should be 2 separate byte arrays by now really?
                else -> throw IllegalArgumentException("invalid sub type")
            }
        }
        BaseDataType.UNDEFINED -> throw IllegalArgumentException("wrong dt")
    }
}

fun convertIRType(typestr: String): IRDataType? {
    return when(typestr.lowercase()) {
        "" -> null
        ".b" -> IRDataType.BYTE
        ".w" -> IRDataType.WORD
        ".l" -> IRDataType.LONG
        ".f" -> IRDataType.FLOAT
        ".p" -> IRDataType.POINTER
        else -> throw IRParseException("invalid type $typestr")
    }
}

fun parseIRValue(value: String): Double {
    return if(value.startsWith("-"))
        -parseIRValue(value.substring(1))
    else if(value.startsWith('$'))
        value.substring(1).toLong(16).toDouble()
    else if(value.startsWith('%'))
        value.substring(1).toLong(2).toDouble()
    else if(value.startsWith("0x"))
        value.substring(2).toLong(16).toDouble()
    else if(value.startsWith('_'))
        throw IRParseException("attempt to parse a label as numeric value")
    else if(value.startsWith('&'))
        throw IRParseException("address-of should be done with normal LOAD <symbol>")
    else if(value.startsWith('@'))
        throw IRParseException("address-of @ should have been handled earlier")
    else
        value.toDouble()
}


sealed interface ParsedIRLine {
    data class Instruction(val value: IRInstruction) : ParsedIRLine
    data class Label(val name: String) : ParsedIRLine
}

private val labelPattern = Regex("""_([a-zA-Z\d\._]+):""")

fun parseIRCodeLine(line: String): ParsedIRLine {
    val labelmatch = labelPattern.matchEntire(line.trim())
    if(labelmatch!=null) {
        val name = labelmatch.groupValues[1]
        try {
            requireValidIRSymbolName(name)
        } catch (ex: IllegalArgumentException) {
            throw IRParseException(ex.message ?: "invalid label: $name")
        }
        return ParsedIRLine.Label(name)
    }
    return ParsedIRLine.Instruction(IRTextCodec.parse(line))
}


internal fun parseRegisterOrStatusflag(sourceregs: String): RegisterOrStatusflag {
    var reg: RegisterOrPair? = null
    var sf: Statusflag? = null

    val regs = if(sourceregs.endsWith(".b") || sourceregs.endsWith(".w") || sourceregs.endsWith(".f"))
        sourceregs.dropLast(2) else sourceregs

    try {
        reg = RegisterOrPair.valueOf(regs)
    } catch (_: IllegalArgumentException) {
        try {
            sf = Statusflag.valueOf(regs)
        } catch(_: IllegalArgumentException) {
            throw IRParseException("invalid IR register or statusflag: $regs")
        }
    }
    return RegisterOrStatusflag(reg, sf)
}
