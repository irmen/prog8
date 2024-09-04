package prog8.code.core

enum class BaseDataType {
    UBYTE,              // pass by value            8 bits unsigned
    BYTE,               // pass by value            8 bits signed
    UWORD,              // pass by value            16 bits unsigned
    WORD,               // pass by value            16 bits signed
    LONG,               // pass by value            32 bits signed
    FLOAT,              // pass by value            machine dependent
    BOOL,               // pass by value            bit 0 of a 8 bit byte
    STR,                // pass by reference
    ARRAY,              // pass by reference, subtype is the element type
    ARRAY_SPLITW,       // pass by reference, split word layout, subtype is the element type (restricted to word types)
    UNDEFINED;


    fun largerSizeThan(other: BaseDataType) =
        when {
            this == other -> false
            this.isByteOrBool -> false
            this.isWord -> other.isByteOrBool
            this == LONG -> other.isByteOrBool || other.isWord
            this == STR && other == UWORD || this == UWORD && other == STR -> false
            this.isArray -> other != FLOAT
            this == STR -> other != FLOAT
            else -> true
        }

    fun equalsSize(other: BaseDataType) =
        when {
            this == other -> true
            this.isByteOrBool -> other.isByteOrBool
            this.isWord -> other.isWord
            this == STR && other== UWORD || this== UWORD && other== STR -> true
            this == STR && other.isArray -> true
            this.isArray && other == STR -> true
            else -> false
        }
}

val BaseDataType.isByte get() = this in arrayOf(BaseDataType.UBYTE, BaseDataType.BYTE)
val BaseDataType.isByteOrBool get() = this in arrayOf(BaseDataType.UBYTE, BaseDataType.BYTE, BaseDataType.BOOL)
val BaseDataType.isWord get() = this in arrayOf(BaseDataType.UWORD, BaseDataType.WORD)
val BaseDataType.isInteger get() = this in arrayOf(BaseDataType.UBYTE, BaseDataType.BYTE, BaseDataType.UWORD, BaseDataType.WORD, BaseDataType.LONG)
val BaseDataType.isIntegerOrBool get() = this in arrayOf(BaseDataType.UBYTE, BaseDataType.BYTE, BaseDataType.UWORD, BaseDataType.WORD, BaseDataType.LONG, BaseDataType.BOOL)
val BaseDataType.isNumeric get() = this == BaseDataType.FLOAT || this.isInteger
val BaseDataType.isNumericOrBool get() = this == BaseDataType.BOOL || this.isNumeric
val BaseDataType.isSigned get() = this in arrayOf(BaseDataType.BYTE, BaseDataType.WORD, BaseDataType.LONG, BaseDataType.FLOAT)
val BaseDataType.isArray get() = this == BaseDataType.ARRAY || this == BaseDataType.ARRAY_SPLITW
val BaseDataType.isSplitWordArray get() = this == BaseDataType.ARRAY_SPLITW
val BaseDataType.isIterable get() =  this in arrayOf(BaseDataType.STR, BaseDataType.ARRAY, BaseDataType.ARRAY_SPLITW)
val BaseDataType.isPassByRef get() = this.isIterable
val BaseDataType.isPassByValue get() = !this.isIterable


sealed class SubType(val dt: BaseDataType) {
    companion object {
        private val types by lazy {
            // lazy because of static initialization order
            mapOf(
                BaseDataType.UBYTE to SubUnsignedByte,
                BaseDataType.BYTE to SubSignedByte,
                BaseDataType.UWORD to SubUnsignedWord,
                BaseDataType.WORD to SubSignedWord,
                BaseDataType.FLOAT to SubFloat,
                BaseDataType.BOOL to SubBool
            )}
        fun forDt(dt: BaseDataType): SubType =
            types.getOrElse(dt) { throw IllegalArgumentException("invalid sub type $dt possible ${types.keys}") }
    }
}

internal object SubUnsignedByte: SubType(BaseDataType.UBYTE)
internal object SubSignedByte: SubType(BaseDataType.BYTE)
internal object SubUnsignedWord: SubType(BaseDataType.UWORD)
internal object SubSignedWord: SubType(BaseDataType.WORD)
internal object SubBool: SubType(BaseDataType.BOOL)
internal object SubFloat: SubType(BaseDataType.FLOAT)



// TODO rename back to DataType once everything has been converted
data class DataTypeFull private constructor(val dt: BaseDataType, val sub: SubType?) {

    init {
        if(dt.isArray) {
            require(sub != null)
            if(dt.isSplitWordArray)
                require(sub.dt == BaseDataType.UWORD || sub.dt == BaseDataType.WORD)
        }
        else if(dt==BaseDataType.STR)
            require(sub?.dt==BaseDataType.UBYTE) { "STR subtype should be ubyte" }
        else
            require(sub == null)
    }

    companion object {
        private val simpletypes = mapOf(
            BaseDataType.UBYTE to DataTypeFull(BaseDataType.UBYTE, null),
            BaseDataType.BYTE to DataTypeFull(BaseDataType.BYTE, null),
            BaseDataType.UWORD to DataTypeFull(BaseDataType.UWORD, null),
            BaseDataType.WORD to DataTypeFull(BaseDataType.WORD, null),
            BaseDataType.LONG to DataTypeFull(BaseDataType.LONG, null),
            BaseDataType.FLOAT to DataTypeFull(BaseDataType.FLOAT, null),
            BaseDataType.BOOL to DataTypeFull(BaseDataType.BOOL, null),
            BaseDataType.STR to DataTypeFull(BaseDataType.STR, SubUnsignedByte),
            BaseDataType.UNDEFINED to DataTypeFull(BaseDataType.UNDEFINED, null)
        )

        fun forDt(dt: BaseDataType): DataTypeFull =
            simpletypes.getOrElse(dt) { throw IllegalArgumentException("invalid data type") }

        fun arrayFor(elementDt: BaseDataType, split: Boolean=false): DataTypeFull {
            val actualElementDt = if(elementDt==BaseDataType.STR) BaseDataType.UWORD else elementDt      // array of strings is actually just an array of UWORD pointers
            if(split) return DataTypeFull(BaseDataType.ARRAY_SPLITW, SubType.forDt(actualElementDt))
            else return DataTypeFull(BaseDataType.ARRAY, SubType.forDt(actualElementDt))
        }
    }

    fun elementToArray(split: Boolean = false): DataTypeFull {
        if(split) {
            return when(dt) {
                BaseDataType.UWORD -> DataTypeFull(BaseDataType.ARRAY_SPLITW, SubUnsignedWord)
                BaseDataType.WORD -> DataTypeFull(BaseDataType.ARRAY_SPLITW, SubSignedWord)
                BaseDataType.STR -> DataTypeFull(BaseDataType.ARRAY_SPLITW, SubUnsignedWord)
                else -> throw IllegalArgumentException("invalid array elt dt")
            }
        }
        return arrayFor(dt)
    }

    fun elementType(): DataTypeFull =
        if(dt.isArray || dt==BaseDataType.STR)
            forDt(sub!!.dt)
        else
            throw IllegalArgumentException("not an array")

    override fun toString(): String = when(dt) {
        BaseDataType.ARRAY -> {
            when(sub) {
                SubBool -> "bool[]"
                SubFloat -> "float[]"
                SubSignedByte -> "byte[]"
                SubSignedWord -> "word[]"
                SubUnsignedByte -> "ubyte[]"
                SubUnsignedWord -> "uword[]"
                null -> throw IllegalArgumentException("invalid sub type")
            }
        }
        BaseDataType.ARRAY_SPLITW -> {
            when(sub) {
                SubSignedWord -> "@split word[]"
                SubUnsignedWord -> "@split uword[]"
                else -> throw IllegalArgumentException("invalid sub type")
            }
        }
        else -> dt.name.lowercase()
    }

    fun sourceString(): String = when (dt) {
        BaseDataType.BOOL -> "bool"
        BaseDataType.UBYTE -> "ubyte"
        BaseDataType.BYTE -> "byte"
        BaseDataType.UWORD -> "uword"
        BaseDataType.WORD -> "word"
        BaseDataType.LONG -> "long"
        BaseDataType.FLOAT -> "float"
        BaseDataType.STR -> "str"
        BaseDataType.ARRAY -> {
            when(sub) {
                SubUnsignedByte -> "ubyte["
                SubUnsignedWord -> "uword["
                SubBool -> "bool["
                SubSignedByte -> "byte["
                SubSignedWord -> "word["
                SubFloat -> "float["
                null -> throw IllegalArgumentException("invalid sub type")
            }
        }
        BaseDataType.ARRAY_SPLITW -> {
            when(sub) {
                SubUnsignedWord -> "@split uword["
                SubSignedWord -> "@split word["
                else -> throw IllegalArgumentException("invalid sub type")
            }
        }
        BaseDataType.UNDEFINED -> throw IllegalArgumentException("wrong dt")
    }

    // is the type assignable to the given other type (perhaps via a typecast) without loss of precision?
    infix fun isAssignableTo(targetType: DataTypeFull) =
        when(dt) {
            BaseDataType.BOOL -> targetType.dt == BaseDataType.BOOL
            BaseDataType.UBYTE -> targetType.dt in arrayOf(BaseDataType.UBYTE, BaseDataType.WORD, BaseDataType.UWORD, BaseDataType.LONG, BaseDataType.FLOAT)
            BaseDataType.BYTE -> targetType.dt in arrayOf(BaseDataType.BYTE, BaseDataType.WORD, BaseDataType.LONG, BaseDataType.FLOAT)
            BaseDataType.UWORD -> targetType.dt in arrayOf(BaseDataType.UWORD, BaseDataType.LONG, BaseDataType.FLOAT)
            BaseDataType.WORD -> targetType.dt in arrayOf(BaseDataType.WORD, BaseDataType.LONG, BaseDataType.FLOAT)
            BaseDataType.LONG -> targetType.dt in arrayOf(BaseDataType.LONG, BaseDataType.FLOAT)
            BaseDataType.FLOAT -> targetType.dt in arrayOf(BaseDataType.FLOAT)
            BaseDataType.STR -> targetType.dt in arrayOf(BaseDataType.STR, BaseDataType.UWORD)
            BaseDataType.ARRAY, BaseDataType.ARRAY_SPLITW -> targetType.dt in arrayOf(BaseDataType.ARRAY, BaseDataType.ARRAY_SPLITW) && targetType.sub == sub
            BaseDataType.UNDEFINED -> false
        }

    fun largerSizeThan(other: DataTypeFull) = dt.largerSizeThan(other.dt)
    fun equalsSize(other: DataTypeFull) = dt.equalsSize(other.dt)

    val isUndefined = dt == BaseDataType.UNDEFINED
    val isByte = dt.isByte
    val isUnsignedByte = dt == BaseDataType.UBYTE
    val isSignedByte = dt == BaseDataType.BYTE
    val isByteOrBool = dt.isByteOrBool
    val isWord = dt.isWord
    val isUnsignedWord =  dt == BaseDataType.UWORD
    val isSignedWord =  dt == BaseDataType.WORD
    val isInteger = dt.isInteger
    val isIntegerOrBool = dt.isIntegerOrBool
    val isNumeric = dt.isNumeric
    val isNumericOrBool = dt.isNumericOrBool
    val isSigned = dt.isSigned
    val isUnsigned = !dt.isSigned
    val isArray = dt.isArray
    val isBoolArray = dt.isArray && sub?.dt == BaseDataType.BOOL
    val isByteArray = dt.isArray && (sub?.dt == BaseDataType.UBYTE || sub?.dt == BaseDataType.BYTE)
    val isUnsignedByteArray = dt.isArray && sub?.dt == BaseDataType.UBYTE
    val isSignedByteArray = dt.isArray && sub?.dt == BaseDataType.BYTE
    val isWordArray = dt.isArray && (sub?.dt == BaseDataType.UWORD || sub?.dt == BaseDataType.WORD)
    val isUnsignedWordArray = dt.isArray && sub?.dt == BaseDataType.UWORD
    val isSignedWordArray = dt.isArray && sub?.dt == BaseDataType.WORD
    val isFloatArray = dt.isArray && sub?.dt == BaseDataType.FLOAT
    val isString = dt == BaseDataType.STR
    val isBool = dt == BaseDataType.BOOL
    val isFloat = dt == BaseDataType.FLOAT
    val isLong = dt == BaseDataType.LONG
    val isStringly = dt == BaseDataType.STR || dt == BaseDataType.UWORD || (dt == BaseDataType.ARRAY && (sub?.dt == BaseDataType.UBYTE || sub?.dt == BaseDataType.BYTE))
    val isSplitWordArray = dt.isSplitWordArray
    val isSplitUnsignedWordArray = dt.isSplitWordArray && sub?.dt == BaseDataType.UWORD
    val isSplitSignedWordArray = dt.isSplitWordArray && sub?.dt == BaseDataType.WORD
    val isIterable =  dt.isIterable
    val isPassByRef = dt.isPassByRef
    val isPassByValue = dt.isPassByValue
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
