package prog8.code.core

import java.util.*

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
    POINTER,            // typed pointer, subtype is whatever type is pointed to
    ARRAY_POINTER,      // array of pointers (uwords), subtype is whatever type each element points to
    UNDEFINED;


    fun largerSizeThan(other: BaseDataType) =
        when {
            this == other -> false
            this.isByteOrBool -> false
            this.isWord -> other.isByteOrBool
            this == LONG -> other.isByteOrBool || other.isWord
            this == STR && other == UWORD || this == UWORD && other == STR -> false
            this.isArray && other.isArray -> false
            this.isArray -> other != FLOAT
            this == STR -> other != FLOAT
            else -> true
        }

    fun equalsSize(other: BaseDataType) =
        when {
            this == other -> true
            this.isArray && other.isArray -> true
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
val BaseDataType.isArray get() = this == BaseDataType.ARRAY || this == BaseDataType.ARRAY_SPLITW || this == BaseDataType.ARRAY_POINTER
val BaseDataType.isPointer get() = this == BaseDataType.POINTER
val BaseDataType.isPointerArray get() = this == BaseDataType.ARRAY_POINTER
val BaseDataType.isSplitWordArray get() = this == BaseDataType.ARRAY_SPLITW
val BaseDataType.isIterable get() =  this in arrayOf(BaseDataType.STR, BaseDataType.ARRAY, BaseDataType.ARRAY_SPLITW, BaseDataType.ARRAY_POINTER)
val BaseDataType.isPassByRef get() = this.isIterable
val BaseDataType.isPassByValue get() = !this.isIterable


class DataType private constructor(val base: BaseDataType, val sub: BaseDataType?, val subIdentifier: List<String>?) {

    init {
        if(base.isPointerArray) {
            require(sub!=null || subIdentifier!=null)
        }
        else if(base.isArray) {
            require(sub != null && subIdentifier==null)
            if(base.isSplitWordArray)
                require(sub == BaseDataType.UWORD || sub == BaseDataType.WORD)
        }
        else if(base==BaseDataType.STR)
            require(sub==BaseDataType.UBYTE) { "string subtype should be ubyte" }
        else if(base!=BaseDataType.POINTER)
            require(sub == null) { "only string, array and pointer base types can have a subtype"}

        require(sub == null || subIdentifier == null) { "subtype and identifier can't both be set" }

    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DataType) return false
        return base == other.base && sub == other.sub
    }

    override fun hashCode(): Int = Objects.hash(base, sub)

    companion object {

        val UBYTE = DataType(BaseDataType.UBYTE, null, null)
        val BYTE = DataType(BaseDataType.BYTE, null, null)
        val UWORD = DataType(BaseDataType.UWORD, null, null)
        val WORD = DataType(BaseDataType.WORD, null, null)
        val LONG = DataType(BaseDataType.LONG, null, null)
        val FLOAT = DataType(BaseDataType.FLOAT, null, null)
        val BOOL = DataType(BaseDataType.BOOL, null, null)
        val STR = DataType(BaseDataType.STR, BaseDataType.UBYTE, null)
        val UNDEFINED = DataType(BaseDataType.UNDEFINED, null, null)

        private val simpletypes = mapOf(
            BaseDataType.UBYTE to DataType(BaseDataType.UBYTE, null, null),
            BaseDataType.BYTE to DataType(BaseDataType.BYTE, null, null),
            BaseDataType.UWORD to DataType(BaseDataType.UWORD, null, null),
            BaseDataType.WORD to DataType(BaseDataType.WORD, null, null),
            BaseDataType.LONG to DataType(BaseDataType.LONG, null, null),
            BaseDataType.FLOAT to DataType(BaseDataType.FLOAT, null, null),
            BaseDataType.BOOL to DataType(BaseDataType.BOOL, null, null),
            BaseDataType.STR to DataType(BaseDataType.STR, BaseDataType.UBYTE, null),
            BaseDataType.UNDEFINED to DataType(BaseDataType.UNDEFINED, null, null)
        )

        fun forDt(dt: BaseDataType) = simpletypes.getValue(dt)

        fun arrayFor(elementDt: BaseDataType, splitwordarray: Boolean=true): DataType {
            require(!elementDt.isPointer) { "use other array constructor for arrays of pointers" }
            val actualElementDt = if(elementDt==BaseDataType.STR) BaseDataType.UWORD else elementDt      // array of strings is actually just an array of UWORD pointers
            return if(splitwordarray && actualElementDt.isWord)
                DataType(BaseDataType.ARRAY_SPLITW, actualElementDt, null)
            else {
                if(actualElementDt.isNumericOrBool && actualElementDt != BaseDataType.LONG)
                    DataType(BaseDataType.ARRAY, actualElementDt, null)
                else
                    throw NoSuchElementException("invalid element dt $elementDt")
            }
        }

        fun arrayOfPointersTo(sub: BaseDataType?, subIdentifier: List<String>?): DataType =
            DataType(BaseDataType.ARRAY_POINTER, sub, subIdentifier)

        fun pointer(base: BaseDataType): DataType = DataType(BaseDataType.POINTER, base, null)

        fun pointer(scopedIdentifier: List<String>): DataType = DataType(BaseDataType.POINTER, null, scopedIdentifier)
    }

    fun elementToArray(splitwords: Boolean = true): DataType {
        return if (base == BaseDataType.UWORD || base == BaseDataType.WORD || base == BaseDataType.STR) arrayFor(base, splitwords)
        else arrayFor(base, false)
    }

    fun elementType(): DataType =
        if(isPointerArray)
            DataType(BaseDataType.POINTER, sub, subIdentifier)
        else if(base.isArray || base==BaseDataType.STR)
            forDt(sub!!)
        else
            throw IllegalArgumentException("not an array")

    override fun toString(): String = when(base) {
        BaseDataType.ARRAY -> {
            when(sub) {
                BaseDataType.BOOL -> "bool[]"
                BaseDataType.FLOAT -> "float[]"
                BaseDataType.BYTE -> "byte[]"
                BaseDataType.WORD -> "word[]"
                BaseDataType.UBYTE -> "ubyte[]"
                BaseDataType.UWORD -> "uword[]"
                else -> throw IllegalArgumentException("invalid sub type")
            }
        }
        BaseDataType.ARRAY_SPLITW -> {
            when(sub) {
                BaseDataType.WORD -> "word[] (split)"
                BaseDataType.UWORD -> "uword[] (split)"
                else -> throw IllegalArgumentException("invalid sub type")
            }
        }
        BaseDataType.POINTER -> {
            if(sub!=null) "^${sub.name.lowercase()}" else "^${subIdentifier!!.joinToString(".")}"
        }
        BaseDataType.ARRAY_POINTER -> {
            if(sub!=null) "^${sub.name.lowercase()}[] (split)" else "^${subIdentifier!!.joinToString(".")}[] (split)"
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
        BaseDataType.POINTER -> {
            if(sub!=null) "^${sub.name.lowercase()}" else "^${subIdentifier!!.joinToString(".")}"
        }
        BaseDataType.ARRAY_POINTER -> {
            if(sub!=null) "^${sub.name.lowercase()}[" else "^${subIdentifier!!.joinToString(".")}["
        }
        BaseDataType.ARRAY -> {
            when(sub) {
                BaseDataType.UBYTE -> "ubyte["
                BaseDataType.UWORD -> "@nosplit uword["
                BaseDataType.BOOL -> "bool["
                BaseDataType.BYTE -> "byte["
                BaseDataType.WORD -> "@nosplit word["
                BaseDataType.FLOAT -> "float["
                else -> throw IllegalArgumentException("invalid sub type")
            }
        }
        BaseDataType.ARRAY_SPLITW -> {
            when(sub) {
                BaseDataType.UWORD -> "uword["
                BaseDataType.WORD -> "word["
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
            BaseDataType.POINTER, BaseDataType.ARRAY_POINTER -> {
                when {
                    targetType.base == BaseDataType.UWORD || targetType.base == BaseDataType.LONG -> true
                    targetType.isPointer -> this.isUnsignedWord
                    else -> false
                }
            }
            BaseDataType.UNDEFINED -> false
        }

    fun largerSizeThan(other: DataType): Boolean = base.largerSizeThan(other.base)
    fun equalsSize(other: DataType): Boolean = base.equalsSize(other.base)

    val isBasic = sub==null && subIdentifier==null
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
    val isPointer = base.isPointer
    val isPointerArray = base.isPointerArray
    val isBoolArray = base.isArray && sub == BaseDataType.BOOL
    val isByteArray = base.isArray && (sub == BaseDataType.UBYTE || sub == BaseDataType.BYTE)
    val isUnsignedByteArray = base.isArray && sub == BaseDataType.UBYTE
    val isSignedByteArray = base.isArray && sub == BaseDataType.BYTE
    val isWordArray = base.isArray && (sub == BaseDataType.UWORD || sub == BaseDataType.WORD)
    val isUnsignedWordArray = base.isArray && sub == BaseDataType.UWORD
    val isSignedWordArray = base.isArray && sub == BaseDataType.WORD
    val isFloatArray = base.isArray && sub == BaseDataType.FLOAT
    val isString = base == BaseDataType.STR
    val isBool = base == BaseDataType.BOOL
    val isFloat = base == BaseDataType.FLOAT
    val isLong = base == BaseDataType.LONG
    val isStringly = base == BaseDataType.STR || base == BaseDataType.UWORD || (base == BaseDataType.ARRAY && (sub == BaseDataType.UBYTE || sub == BaseDataType.BYTE))
    val isSplitWordArray = base.isSplitWordArray
    val isSplitUnsignedWordArray = base.isSplitWordArray && sub == BaseDataType.UWORD
    val isSplitSignedWordArray = base.isSplitWordArray && sub == BaseDataType.WORD
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
    XEX,
    LIBRARY
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

enum class SplitWish {
    DONTCARE,
    SPLIT,
    NOSPLIT
}