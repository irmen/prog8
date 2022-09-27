package prog8.intermediate

import prog8.code.StMemVar
import prog8.code.StStaticVariable
import prog8.code.core.DataType
import prog8.code.core.InternalCompilerException


fun getTypeString(dt : DataType): String {
    return when(dt) {
        DataType.UBYTE -> "ubyte"
        DataType.BYTE -> "byte"
        DataType.UWORD -> "uword"
        DataType.WORD -> "word"
        DataType.FLOAT -> "float"
        DataType.ARRAY_UB, DataType.STR -> "ubyte[]"
        DataType.ARRAY_B -> "byte[]"
        DataType.ARRAY_UW -> "uword[]"
        DataType.ARRAY_W -> "word[]"
        DataType.ARRAY_F -> "float[]"
        else -> throw InternalCompilerException("weird dt")
    }
}

fun getTypeString(memvar: StMemVar): String {
    return when(memvar.dt) {
        DataType.UBYTE -> "ubyte"
        DataType.BYTE -> "byte"
        DataType.UWORD -> "uword"
        DataType.WORD -> "word"
        DataType.FLOAT -> "float"
        DataType.ARRAY_UB, DataType.STR -> "ubyte[${memvar.length}]"
        DataType.ARRAY_B -> "byte[${memvar.length}]"
        DataType.ARRAY_UW -> "uword[${memvar.length}]"
        DataType.ARRAY_W -> "word[${memvar.length}]"
        DataType.ARRAY_F -> "float[${memvar.length}]"
        else -> throw InternalCompilerException("weird dt")
    }
}

fun getTypeString(variable : StStaticVariable): String {
    return when(variable.dt) {
        DataType.UBYTE -> "ubyte"
        DataType.BYTE -> "byte"
        DataType.UWORD -> "uword"
        DataType.WORD -> "word"
        DataType.FLOAT -> "float"
        DataType.ARRAY_UB, DataType.STR -> "ubyte[${variable.length}]"
        DataType.ARRAY_B -> "byte[${variable.length}]"
        DataType.ARRAY_UW -> "uword[${variable.length}]"
        DataType.ARRAY_W -> "word[${variable.length}]"
        DataType.ARRAY_F -> "float[${variable.length}]"
        else -> throw InternalCompilerException("weird dt")
    }
}

fun convertIRType(typestr: String): VmDataType? {
    return when(typestr.lowercase()) {
        "" -> null
        ".b" -> VmDataType.BYTE
        ".w" -> VmDataType.WORD
        ".f" -> VmDataType.FLOAT
        else -> throw IRParseException("invalid type $typestr")
    }
}

fun parseIRValue(value: String): Float {
    return if(value.startsWith("-"))
        -parseIRValue(value.substring(1))
    else if(value.startsWith('$'))
        value.substring(1).toInt(16).toFloat()
    else if(value.startsWith('%'))
        value.substring(1).toInt(2).toFloat()
    else if(value.startsWith("0x"))
        value.substring(2).toInt(16).toFloat()
    else if(value.startsWith('_'))
        throw IRParseException("attempt to parse a label as numeric value")
    else if(value.startsWith('&'))
        throw IRParseException("address-of should be done with normal LOAD <symbol>")
    else
        return value.toFloat()
}