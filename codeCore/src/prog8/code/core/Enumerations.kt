package prog8.code.core

import java.util.Objects

enum class BaseDataType {
    UBYTE,              // pass by value            8 bits unsigned
    BYTE,               // pass by value            8 bits signed
    UWORD,              // pass by value            16 bits unsigned
    WORD,               // pass by value            16 bits signed
    LONG,               // pass by value            32 bits signed
    FLOAT,              // pass by value            machine dependent
    BOOL,               // pass by value            bit 0 of an 8-bit byte
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

        fun forDt(dt: BaseDataType) = types.getValue(dt)
    }
}

private data object SubUnsignedByte: SubType(BaseDataType.UBYTE)
private data object SubSignedByte: SubType(BaseDataType.BYTE)
private data object SubUnsignedWord: SubType(BaseDataType.UWORD)
private data object SubSignedWord: SubType(BaseDataType.WORD)
private data object SubBool: SubType(BaseDataType.BOOL)
private data object SubFloat: SubType(BaseDataType.FLOAT)


class DataType private constructor(val base: BaseDataType, val sub: SubType?) {

    init {
        if(base.isArray) {
            require(sub != null)
            if(base.isSplitWordArray)
                require(sub.dt == BaseDataType.UWORD || sub.dt == BaseDataType.WORD)
        }
        else if(base==BaseDataType.STR)
            require(sub?.dt==BaseDataType.UBYTE) { "STR subtype should be ubyte" }
        else
            require(sub == null)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DataType) return false
        return base == other.base && sub == other.sub
    }

    override fun hashCode(): Int = Objects.hash(base, sub)

    companion object {
        private val simpletypes = mapOf(
            BaseDataType.UBYTE to DataType(BaseDataType.UBYTE, null),
            BaseDataType.BYTE to DataType(BaseDataType.BYTE, null),
            BaseDataType.UWORD to DataType(BaseDataType.UWORD, null),
            BaseDataType.WORD to DataType(BaseDataType.WORD, null),
            BaseDataType.LONG to DataType(BaseDataType.LONG, null),
            BaseDataType.FLOAT to DataType(BaseDataType.FLOAT, null),
            BaseDataType.BOOL to DataType(BaseDataType.BOOL, null),
            BaseDataType.STR to DataType(BaseDataType.STR, SubUnsignedByte),
            BaseDataType.UNDEFINED to DataType(BaseDataType.UNDEFINED, null)
        )

        fun forDt(dt: BaseDataType) = simpletypes.getValue(dt)

        fun arrayFor(elementDt: BaseDataType, split: Boolean=false): DataType {
            val actualElementDt = if(elementDt==BaseDataType.STR) BaseDataType.UWORD else elementDt      // array of strings is actually just an array of UWORD pointers
            if(split) return DataType(BaseDataType.ARRAY_SPLITW, SubType.forDt(actualElementDt))
            else return DataType(BaseDataType.ARRAY, SubType.forDt(actualElementDt))
        }
    }

    fun elementToArray(split: Boolean = false): DataType {
        if(split) {
            return if (base == BaseDataType.UWORD || base == BaseDataType.WORD || base == BaseDataType.STR) arrayFor(base, true)
            else throw IllegalArgumentException("invalid split array elt dt")
        }
        return arrayFor(base)
    }

    fun elementType(): DataType =
        if(base.isArray || base==BaseDataType.STR)
            forDt(sub!!.dt)
        else
            throw IllegalArgumentException("not an array")

    override fun toString(): String = when(base) {
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
        else -> base.name.lowercase()
    }

    fun sourceString(): String = when (base) {
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
    infix fun isAssignableTo(targetType: DataType) =
        when(base) {
            BaseDataType.BOOL -> targetType.base == BaseDataType.BOOL
            BaseDataType.UBYTE -> targetType.base in arrayOf(BaseDataType.UBYTE, BaseDataType.WORD, BaseDataType.UWORD, BaseDataType.LONG, BaseDataType.FLOAT)
            BaseDataType.BYTE -> targetType.base in arrayOf(BaseDataType.BYTE, BaseDataType.WORD, BaseDataType.LONG, BaseDataType.FLOAT)
            BaseDataType.UWORD -> targetType.base in arrayOf(BaseDataType.UWORD, BaseDataType.LONG, BaseDataType.FLOAT)
            BaseDataType.WORD -> targetType.base in arrayOf(BaseDataType.WORD, BaseDataType.LONG, BaseDataType.FLOAT)
            BaseDataType.LONG -> targetType.base in arrayOf(BaseDataType.LONG, BaseDataType.FLOAT)
            BaseDataType.FLOAT -> targetType.base in arrayOf(BaseDataType.FLOAT)
            BaseDataType.STR -> targetType.base in arrayOf(BaseDataType.STR, BaseDataType.UWORD)
            BaseDataType.ARRAY, BaseDataType.ARRAY_SPLITW -> targetType.base in arrayOf(BaseDataType.ARRAY, BaseDataType.ARRAY_SPLITW) && targetType.sub == sub
            BaseDataType.UNDEFINED -> false
        }

    fun largerSizeThan(other: DataType): Boolean {
        if(isArray) throw IllegalArgumentException("cannot compare size of array types")
        return base.largerSizeThan(other.base)
    }
    fun equalsSize(other: DataType): Boolean {
        if(isArray) throw IllegalArgumentException("cannot compare size of array types")
        return base.equalsSize(other.base)
    }

    val isUndefined = base == BaseDataType.UNDEFINED
    val isByte = base.isByte
    val isUnsignedByte = base == BaseDataType.UBYTE
    val isSignedByte = base == BaseDataType.BYTE
    val isByteOrBool = base.isByteOrBool
    val isWord = base.isWord
    val isUnsignedWord =  base == BaseDataType.UWORD
    val isSignedWord =  base == BaseDataType.WORD
    val isInteger = base.isInteger
    val isIntegerOrBool = base.isIntegerOrBool
    val isNumeric = base.isNumeric
    val isNumericOrBool = base.isNumericOrBool
    val isSigned = base.isSigned
    val isUnsigned = !base.isSigned
    val isArray = base.isArray
    val isBoolArray = base.isArray && sub?.dt == BaseDataType.BOOL
    val isByteArray = base.isArray && (sub?.dt == BaseDataType.UBYTE || sub?.dt == BaseDataType.BYTE)
    val isUnsignedByteArray = base.isArray && sub?.dt == BaseDataType.UBYTE
    val isSignedByteArray = base.isArray && sub?.dt == BaseDataType.BYTE
    val isWordArray = base.isArray && (sub?.dt == BaseDataType.UWORD || sub?.dt == BaseDataType.WORD)
    val isUnsignedWordArray = base.isArray && sub?.dt == BaseDataType.UWORD
    val isSignedWordArray = base.isArray && sub?.dt == BaseDataType.WORD
    val isFloatArray = base.isArray && sub?.dt == BaseDataType.FLOAT
    val isString = base == BaseDataType.STR
    val isBool = base == BaseDataType.BOOL
    val isFloat = base == BaseDataType.FLOAT
    val isLong = base == BaseDataType.LONG
    val isStringly = base == BaseDataType.STR || base == BaseDataType.UWORD || (base == BaseDataType.ARRAY && (sub?.dt == BaseDataType.UBYTE || sub?.dt == BaseDataType.BYTE))
    val isSplitWordArray = base.isSplitWordArray
    val isSplitUnsignedWordArray = base.isSplitWordArray && sub?.dt == BaseDataType.UWORD
    val isSplitSignedWordArray = base.isSplitWordArray && sub?.dt == BaseDataType.WORD
    val isIterable =  base.isIterable
    val isPassByRef = base.isPassByRef
    val isPassByValue = base.isPassByValue
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
        val suffix = when(type?.base) {
            BaseDataType.UBYTE, BaseDataType.BOOL -> "L"
            BaseDataType.BYTE -> "sL"
            BaseDataType.WORD -> "s"
            BaseDataType.UWORD, null -> ""
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

val Cx16VirtualRegisters = arrayOf(
    RegisterOrPair.R0, RegisterOrPair.R1, RegisterOrPair.R2, RegisterOrPair.R3,
    RegisterOrPair.R4, RegisterOrPair.R5, RegisterOrPair.R6, RegisterOrPair.R7,
    RegisterOrPair.R8, RegisterOrPair.R9, RegisterOrPair.R10, RegisterOrPair.R11,
    RegisterOrPair.R12, RegisterOrPair.R13, RegisterOrPair.R14, RegisterOrPair.R15
)

val CpuRegisters = arrayOf(
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
