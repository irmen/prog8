package prog8.code.core

enum class DataType {
    UBYTE,              // pass by value            8 bits unsigned
    BYTE,               // pass by value            8 bits signed
    UWORD,              // pass by value            16 bits unsigned
    WORD,               // pass by value            16 bits signed
    LONG,               // pass by value            32 bits signed
    FLOAT,              // pass by value            machine dependent
    BOOL,               // pass by value            bit 0 of an 8-bit byte
    STR,                // pass by reference
    ARRAY_UB,           // pass by reference
    ARRAY_B,            // pass by reference
    ARRAY_UW,           // pass by reference
    ARRAY_UW_SPLIT,     // pass by reference, lo/hi byte split
    ARRAY_W,            // pass by reference
    ARRAY_W_SPLIT,      // pass by reference, lo/hi byte split
    ARRAY_F,            // pass by reference
    ARRAY_BOOL,         // pass by reference
    UNDEFINED;

    /**
     * is the type assignable to the given other type (perhaps via a typecast) without loss of precision?
     */
    infix fun isAssignableTo(targetType: DataType) =
        when(this) {
            BOOL -> targetType == BOOL
            UBYTE -> targetType.oneOf(UBYTE, WORD, UWORD, LONG, FLOAT)
            BYTE -> targetType.oneOf(BYTE, WORD, LONG, FLOAT)
            UWORD -> targetType.oneOf(UWORD, LONG, FLOAT)
            WORD -> targetType.oneOf(WORD, LONG, FLOAT)
            LONG -> targetType.oneOf(LONG, FLOAT)
            FLOAT -> targetType.oneOf(FLOAT)
            STR -> targetType.oneOf(STR, UWORD)
            in ArrayDatatypes -> targetType == this
            else -> false
        }

    fun oneOf(vararg types: DataType) = this in types

    infix fun largerThan(other: DataType) =
        when {
            this == other -> false
            this in ByteDatatypesWithBoolean -> false
            this in WordDatatypes -> other in ByteDatatypesWithBoolean
            this == LONG -> other in ByteDatatypesWithBoolean+WordDatatypes
            this == STR && other == UWORD || this == UWORD && other == STR -> false
            else -> true
        }

    infix fun equalsSize(other: DataType) =
        when {
            this == other -> true
            this in ByteDatatypesWithBoolean -> other in ByteDatatypesWithBoolean
            this in WordDatatypes -> other in WordDatatypes
            this== STR && other== UWORD || this== UWORD && other== STR -> true
            else -> false
        }
}

enum class CpuRegister {
    A,
    X,
    Y
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
        val names by lazy { entries.map { it.toString()} }
        fun fromCpuRegister(cpu: CpuRegister): RegisterOrPair {
            return when(cpu) {
                CpuRegister.A -> A
                CpuRegister.X -> X
                CpuRegister.Y -> Y
            }
        }
    }

    fun asCpuRegister(): CpuRegister = when(this) {
        A -> CpuRegister.A
        X -> CpuRegister.X
        Y -> CpuRegister.Y
        else -> throw IllegalArgumentException("no cpu hardware register for $this")
    }

    fun asScopedNameVirtualReg(type: DataType?): List<String> {
        require(this in Cx16VirtualRegisters)
        val suffix = when(type) {
            DataType.UBYTE, DataType.BOOL -> "L"
            DataType.BYTE -> "sL"
            DataType.WORD -> "s"
            DataType.UWORD, null -> ""
            else -> throw kotlin.IllegalArgumentException("invalid register param type")
        }
        return listOf("cx16", name.lowercase()+suffix)
    }
}       // only used in parameter and return value specs in asm subroutines

enum class Statusflag {
    Pc,
    Pz,     // don't use
    Pv,
    Pn;     // don't use

    companion object {
        val names by lazy { entries.map { it.toString()} }
    }
}

enum class BranchCondition {
    CS,
    CC,
    EQ,     // EQ == Z
    Z,
    NE,     // NE == NZ
    NZ,
    MI,     // MI == NEG
    NEG,
    PL,     // PL == POS
    POS,
    VS,
    VC
}


val ByteDatatypes = arrayOf(DataType.UBYTE, DataType.BYTE)
val ByteDatatypesWithBoolean = ByteDatatypes + DataType.BOOL
val WordDatatypes = arrayOf(DataType.UWORD, DataType.WORD)
val IntegerDatatypes = arrayOf(DataType.UBYTE, DataType.BYTE, DataType.UWORD, DataType.WORD, DataType.LONG)
val IntegerDatatypesWithBoolean = IntegerDatatypes + DataType.BOOL
val NumericDatatypes = arrayOf(DataType.UBYTE, DataType.BYTE, DataType.UWORD, DataType.WORD, DataType.LONG, DataType.FLOAT)
val NumericDatatypesWithBoolean = NumericDatatypes + DataType.BOOL
val SignedDatatypes =  arrayOf(DataType.BYTE, DataType.WORD, DataType.LONG, DataType.FLOAT)
val ArrayDatatypes = arrayOf(DataType.ARRAY_UB, DataType.ARRAY_B, DataType.ARRAY_UW, DataType.ARRAY_UW_SPLIT, DataType.ARRAY_W, DataType.ARRAY_W_SPLIT, DataType.ARRAY_F, DataType.ARRAY_BOOL)
val StringlyDatatypes = arrayOf(DataType.STR, DataType.ARRAY_UB, DataType.ARRAY_B, DataType.UWORD)
val SplitWordArrayTypes = arrayOf(DataType.ARRAY_UW_SPLIT, DataType.ARRAY_W_SPLIT)
val IterableDatatypes = arrayOf(
    DataType.STR,
    DataType.ARRAY_UB, DataType.ARRAY_B,
    DataType.ARRAY_UW, DataType.ARRAY_W,
    DataType.ARRAY_UW_SPLIT, DataType.ARRAY_W_SPLIT,
    DataType.ARRAY_F, DataType.ARRAY_BOOL
)
val PassByValueDatatypes = NumericDatatypesWithBoolean
val PassByReferenceDatatypes = IterableDatatypes
val ArrayToElementTypes = mapOf(
    DataType.STR to DataType.UBYTE,
    DataType.ARRAY_B to DataType.BYTE,
    DataType.ARRAY_UB to DataType.UBYTE,
    DataType.ARRAY_W to DataType.WORD,
    DataType.ARRAY_UW to DataType.UWORD,
    DataType.ARRAY_W_SPLIT to DataType.WORD,
    DataType.ARRAY_UW_SPLIT to DataType.UWORD,
    DataType.ARRAY_F to DataType.FLOAT,
    DataType.ARRAY_BOOL to DataType.BOOL
)
val ElementToArrayTypes = mapOf(
    DataType.BYTE to DataType.ARRAY_B,
    DataType.UBYTE to DataType.ARRAY_UB,
    DataType.WORD to DataType.ARRAY_W,
    DataType.UWORD to DataType.ARRAY_UW,
    DataType.FLOAT to DataType.ARRAY_F,
    DataType.BOOL to DataType.ARRAY_BOOL,
    DataType.STR to DataType.ARRAY_UW          // array of str is just an array of pointers
)

val Cx16VirtualRegisters = arrayOf(
    RegisterOrPair.R0, RegisterOrPair.R1, RegisterOrPair.R2, RegisterOrPair.R3,
    RegisterOrPair.R4, RegisterOrPair.R5, RegisterOrPair.R6, RegisterOrPair.R7,
    RegisterOrPair.R8, RegisterOrPair.R9, RegisterOrPair.R10, RegisterOrPair.R11,
    RegisterOrPair.R12, RegisterOrPair.R13, RegisterOrPair.R14, RegisterOrPair.R15
)

val CpuRegisters = setOf(
    RegisterOrPair.A, RegisterOrPair.X, RegisterOrPair.Y,
    RegisterOrPair.AX, RegisterOrPair.AY, RegisterOrPair.XY
)


enum class OutputType {
    RAW,
    PRG,
    XEX
}

enum class CbmPrgLauncherType {
    BASIC,
    NONE
}

enum class ZeropageType {
    BASICSAFE,
    FLOATSAFE,
    KERNALSAFE,
    FULL,
    DONTUSE
}

enum class ZeropageWish {
    REQUIRE_ZEROPAGE,
    PREFER_ZEROPAGE,
    DONTCARE,
    NOT_IN_ZEROPAGE
}
