package prog8.ast.base

import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.math.abs


/**************************** AST Data classes ****************************/

enum class DataType {
    UBYTE,              // pass by value
    BYTE,               // pass by value
    UWORD,              // pass by value
    WORD,               // pass by value
    FLOAT,              // pass by value
    STR,                // pass by reference
    ARRAY_UB,           // pass by reference
    ARRAY_B,            // pass by reference
    ARRAY_UW,           // pass by reference
    ARRAY_W,            // pass by reference
    ARRAY_F,            // pass by reference
    UNDEFINED;

    /**
     * is the type assignable to the given other type (perhaps via a typecast) without loss of precision?
     */
    infix fun isAssignableTo(targetType: DataType) =
            when(this) {
                UBYTE -> targetType.oneOf(UBYTE, WORD, UWORD, FLOAT)
                BYTE -> targetType.oneOf(BYTE, WORD, FLOAT)
                UWORD -> targetType.oneOf(UWORD, FLOAT)
                WORD -> targetType.oneOf(WORD, FLOAT)
                FLOAT -> targetType == FLOAT
                STR -> targetType.oneOf(STR, UWORD)
                in ArrayDatatypes -> targetType == this
                else -> false
            }

    fun oneOf(vararg types: DataType) = this in types

    infix fun largerThan(other: DataType) =
            when {
                this == other -> false
                this in ByteDatatypes -> false
                this in WordDatatypes -> other in ByteDatatypes
                this== STR && other== UWORD || this== UWORD && other== STR -> false
                else -> true
            }

    infix fun equalsSize(other: DataType) =
            when {
                this == other -> true
                this in ByteDatatypes -> other in ByteDatatypes
                this in WordDatatypes -> other in WordDatatypes
                this== STR && other== UWORD || this== UWORD && other== STR -> true
                else -> false
            }
}

enum class CpuRegister {
    A,
    X,
    Y;

    fun asRegisterOrPair(): RegisterOrPair = when(this) {
        A -> RegisterOrPair.A
        X -> RegisterOrPair.X
        Y -> RegisterOrPair.Y
    }
}

enum class RegisterOrPair {
    A,
    X,
    Y,
    AX,
    AY,
    XY,
    FAC1,
    FAC2,
    // cx16 virtual registers:
    R0, R1, R2, R3, R4, R5, R6, R7,
    R8, R9, R10, R11, R12, R13, R14, R15;

    companion object {
        val names by lazy { values().map { it.toString()} }
    }

    fun asCpuRegister(): CpuRegister = when(this) {
        A -> CpuRegister.A
        X -> CpuRegister.X
        Y -> CpuRegister.Y
        else -> throw IllegalArgumentException("no cpu hardware register for $this")
    }

}       // only used in parameter and return value specs in asm subroutines

enum class Statusflag {
    Pc,
    Pz,     // don't use
    Pv,
    Pn;     // don't use

    companion object {
        val names by lazy { values().map { it.toString()} }
    }
}

enum class BranchCondition {
    CS,
    CC,
    EQ,
    Z,
    NE,
    NZ,
    VS,
    VC,
    MI,
    NEG,
    PL,
    POS
}

enum class VarDeclType {
    VAR,
    CONST,
    MEMORY
}

val ByteDatatypes = arrayOf(DataType.UBYTE, DataType.BYTE)
val WordDatatypes = arrayOf(DataType.UWORD, DataType.WORD)
val IntegerDatatypes = arrayOf(DataType.UBYTE, DataType.BYTE, DataType.UWORD, DataType.WORD)
val NumericDatatypes = arrayOf(DataType.UBYTE, DataType.BYTE, DataType.UWORD, DataType.WORD, DataType.FLOAT)
val SignedDatatypes =  arrayOf(DataType.BYTE, DataType.WORD, DataType.FLOAT)
val ArrayDatatypes = arrayOf(DataType.ARRAY_UB, DataType.ARRAY_B, DataType.ARRAY_UW, DataType.ARRAY_W, DataType.ARRAY_F)
val StringlyDatatypes = arrayOf(DataType.STR, DataType.ARRAY_UB, DataType.ARRAY_B, DataType.UWORD)
val IterableDatatypes = arrayOf(
    DataType.STR,
    DataType.ARRAY_UB, DataType.ARRAY_B,
    DataType.ARRAY_UW, DataType.ARRAY_W,
    DataType.ARRAY_F
)
val PassByValueDatatypes = NumericDatatypes
val PassByReferenceDatatypes = IterableDatatypes
val ArrayToElementTypes = mapOf(
        DataType.STR to DataType.UBYTE,
        DataType.ARRAY_B to DataType.BYTE,
        DataType.ARRAY_UB to DataType.UBYTE,
        DataType.ARRAY_W to DataType.WORD,
        DataType.ARRAY_UW to DataType.UWORD,
        DataType.ARRAY_F to DataType.FLOAT
)
val ElementToArrayTypes = mapOf(
        DataType.BYTE to DataType.ARRAY_B,
        DataType.UBYTE to DataType.ARRAY_UB,
        DataType.WORD to DataType.ARRAY_W,
        DataType.UWORD to DataType.ARRAY_UW,
        DataType.FLOAT to DataType.ARRAY_F
)
val Cx16VirtualRegisters = arrayOf(
    RegisterOrPair.R0, RegisterOrPair.R1, RegisterOrPair.R2, RegisterOrPair.R3,
    RegisterOrPair.R4, RegisterOrPair.R5, RegisterOrPair.R6, RegisterOrPair.R7,
    RegisterOrPair.R8, RegisterOrPair.R9, RegisterOrPair.R10, RegisterOrPair.R11,
    RegisterOrPair.R12, RegisterOrPair.R13, RegisterOrPair.R14, RegisterOrPair.R15
)


data class Position(val file: String, val line: Int, val startCol: Int, val endCol: Int) {
    override fun toString(): String = "[$file: line $line col ${startCol+1}-${endCol+1}]"
    fun toClickableStr(): String {
        val path = Path(file).absolute().normalize()
        return "file://$path:$line:$startCol:"
    }

    companion object {
        val DUMMY = Position("<dummy>", 0, 0, 0)
    }
}


fun Number.toHex(): String {
    //  0..15 -> "0".."15"
    //  16..255 -> "$10".."$ff"
    //  256..65536 -> "$0100".."$ffff"
    // negative values are prefixed with '-'.
    val integer = this.toInt()
    if(integer<0)
        return '-' + abs(integer).toHex()
    return when (integer) {
        in 0 until 16 -> integer.toString()
        in 0 until 0x100 -> "$"+integer.toString(16).padStart(2,'0')
        in 0 until 0x10000 -> "$"+integer.toString(16).padStart(4,'0')
        else -> throw IllegalArgumentException("number too large for 16 bits $this")
    }
}

fun UInt.toHex(): String {
    //  0..15 -> "0".."15"
    //  16..255 -> "$10".."$ff"
    //  256..65536 -> "$0100".."$ffff"
    return when (this) {
        in 0u until 16u -> this.toString()
        in 0u until 0x100u -> "$"+this.toString(16).padStart(2,'0')
        in 0u until 0x10000u -> "$"+this.toString(16).padStart(4,'0')
        else -> throw IllegalArgumentException("number too large for 16 bits $this")
    }
}
