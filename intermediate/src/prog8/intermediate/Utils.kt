package prog8.intermediate

import prog8.code.StMemVar
import prog8.code.StStaticVariable
import prog8.code.core.DataType
import prog8.code.core.InternalCompilerException


public fun getTypeString(dt : DataType): String {
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

public fun getTypeString(memvar: StMemVar): String {
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

public fun getTypeString(variable : StStaticVariable): String {
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
