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
    STRUCT_INSTANCE,    // the actual instance of a struct (not directly supported in the language yet, but we need its type)
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
            this.isPointer -> other.isByteOrBool
            else -> true
        }

    fun equalsSize(other: BaseDataType) =
        when {
            this == other -> true
            this.isArray && other.isArray -> true
            this.isByteOrBool -> other.isByteOrBool
            this.isWord -> other.isWord || other.isPointer
            this.isPointer -> other.isWord
            this == STR && other== UWORD || this== UWORD && other== STR -> true
            this == STR && other.isArray -> true
            this.isArray && other == STR -> true
            else -> false
        }
}

val BaseDataType.isByte get() = this in arrayOf(BaseDataType.UBYTE, BaseDataType.BYTE)
val BaseDataType.isByteOrBool get() = this in arrayOf(BaseDataType.UBYTE, BaseDataType.BYTE, BaseDataType.BOOL)
val BaseDataType.isWord get() = this in arrayOf(BaseDataType.UWORD, BaseDataType.WORD)
val BaseDataType.isLong get() = this == BaseDataType.LONG
val BaseDataType.isFloat get() = this == BaseDataType.FLOAT
val BaseDataType.isInteger get() = this in arrayOf(BaseDataType.UBYTE, BaseDataType.BYTE, BaseDataType.UWORD, BaseDataType.WORD, BaseDataType.LONG)
val BaseDataType.isIntegerOrBool get() = this in arrayOf(BaseDataType.UBYTE, BaseDataType.BYTE, BaseDataType.UWORD, BaseDataType.WORD, BaseDataType.LONG, BaseDataType.BOOL)
val BaseDataType.isWordOrByteOrBool get() = this in arrayOf(BaseDataType.UBYTE, BaseDataType.BYTE, BaseDataType.UWORD, BaseDataType.WORD, BaseDataType.BOOL)
val BaseDataType.isNumeric get() = this == BaseDataType.FLOAT || this.isInteger
val BaseDataType.isNumericOrBool get() = this == BaseDataType.BOOL || this.isNumeric
val BaseDataType.isSigned get() = this in arrayOf(BaseDataType.BYTE, BaseDataType.WORD, BaseDataType.LONG, BaseDataType.FLOAT)
val BaseDataType.isArray get() = this == BaseDataType.ARRAY || this == BaseDataType.ARRAY_SPLITW || this == BaseDataType.ARRAY_POINTER
val BaseDataType.isPointer get() = this == BaseDataType.POINTER
val BaseDataType.isStructInstance get() = this == BaseDataType.STRUCT_INSTANCE
val BaseDataType.isPointerArray get() = this == BaseDataType.ARRAY_POINTER
val BaseDataType.isSplitWordArray get() = this == BaseDataType.ARRAY_SPLITW || this == BaseDataType.ARRAY_POINTER       // pointer arrays are also always stored as split uwords
val BaseDataType.isIterable get() =  this in arrayOf(BaseDataType.STR, BaseDataType.ARRAY, BaseDataType.ARRAY_SPLITW, BaseDataType.ARRAY_POINTER)
val BaseDataType.isPassByRef get() = this.isIterable && !this.isPointer
val BaseDataType.isPassByValue get() = !this.isIterable || this.isPointer


interface ISubType {
    val scopedNameString: String
    fun memsize(sizer: IMemSizer): Int
    fun sameas(other: ISubType): Boolean
    fun getFieldType(name: String): DataType?
}

class DataType private constructor(val base: BaseDataType, val sub: BaseDataType?, var subType: ISubType?, var subTypeFromAntlr: List<String>?=null) {

    init {
        when {
            base.isPointerArray -> {
                require(sub!=null || subType!=null || subTypeFromAntlr!=null)
            }
            base.isArray -> {
                require(sub != null && subType==null && subTypeFromAntlr==null)
                if(base.isSplitWordArray)
                    require(sub == BaseDataType.UWORD || sub == BaseDataType.WORD)
            }
            base==BaseDataType.STR -> require(sub==BaseDataType.UBYTE) { "string subtype should be ubyte" }
            base!=BaseDataType.POINTER -> require(sub == null) { "only string, array and pointer base types can have a subtype"}
            else -> {
                require(sub == null || (subType == null && subTypeFromAntlr == null)) {
                    "sub and subtype can't both be set"
                }
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DataType) return false
        return base == other.base && sub == other.sub && (subType==other.subType || subType!!.sameas(other.subType!!))
    }

    override fun hashCode(): Int = Objects.hash(base, sub, subType)

    fun setActualSubType(actualSubType: ISubType) {
        subType = actualSubType
        subTypeFromAntlr = null
    }

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

        fun forDt(dt: BaseDataType): DataType {
            if(dt.isStructInstance)
                TODO("cannot use struct instance as a data type (yet) - use a pointer instead")
            return simpletypes.getValue(dt)
        }

        fun arrayFor(elementDt: BaseDataType, splitwordarray: Boolean=true): DataType {
            require(!elementDt.isPointer) { "use other array constructor for arrays of pointers" }
            val actualElementDt = if(elementDt==BaseDataType.STR) BaseDataType.UWORD else elementDt      // array of strings is actually just an array of UWORD pointers
            return if(splitwordarray && actualElementDt.isWord)
                DataType(BaseDataType.ARRAY_SPLITW, actualElementDt, null)
            else {
                if(actualElementDt.isNumericOrBool)
                    DataType(BaseDataType.ARRAY, actualElementDt, null)
                else
                    throw NoSuchElementException("invalid basic element dt $elementDt")
            }
        }

        fun arrayOfPointersTo(sub: BaseDataType): DataType = DataType(BaseDataType.ARRAY_POINTER, sub, null)
        fun arrayOfPointersTo(structType: ISubType?): DataType = DataType(BaseDataType.ARRAY_POINTER, null, structType)
        fun arrayOfPointersFromAntlrTo(sub: BaseDataType?, identifier: List<String>?): DataType =
            DataType(BaseDataType.ARRAY_POINTER, sub, null, identifier)

        fun pointer(base: BaseDataType): DataType = DataType(BaseDataType.POINTER, base, null)
        fun pointer(dt: DataType): DataType = if(dt.isBasic)
                DataType(BaseDataType.POINTER, dt.base, null)
            else
                DataType(BaseDataType.POINTER, null, dt.subType, dt.subTypeFromAntlr)
        fun pointer(structType: ISubType): DataType = DataType(BaseDataType.POINTER, null, structType)
        fun pointerFromAntlr(identifier: List<String>): DataType = DataType(BaseDataType.POINTER, null, null, identifier)
        fun structInstance(type: ISubType?): DataType = DataType(BaseDataType.STRUCT_INSTANCE, sub=null, type)
        fun structInstanceFromAntlr(struct: List<String>): DataType = DataType(BaseDataType.STRUCT_INSTANCE, null, null, subTypeFromAntlr = struct)
    }


    fun elementToArray(splitwords: Boolean = true): DataType {
        return if (base == BaseDataType.UWORD || base == BaseDataType.WORD || base == BaseDataType.STR) arrayFor(base, splitwords)
        else arrayFor(base, false)
    }

    fun elementType(): DataType =
        when {
            isPointerArray -> DataType(BaseDataType.POINTER, sub, subType)
            base.isArray || base==BaseDataType.STR -> forDt(sub!!)
            else -> throw IllegalArgumentException("not an array")
        }

    fun typeForAddressOf(msb: Boolean): DataType {
        if (isUndefined)
            return if(msb) pointer(BaseDataType.UBYTE) else UWORD
        else {
            if (isBasic)
                return pointer(base)
            if (isString)
                return pointer(BaseDataType.UBYTE)
            if (isPointer)
                return UWORD
            if (isArray) {
                if (msb || isSplitWordArray)
                    return pointer(BaseDataType.UBYTE)
                val elementDt = elementType()
                require(elementDt.isBasic)
                return pointer(elementDt)
            }
            if (subType != null)
                return pointer(this)
            return UWORD
        }
    }

    fun dereference(): DataType {
        require(isPointer || isUnsignedWord)
        return when {
            isUnsignedWord -> forDt(BaseDataType.UBYTE)
            sub!=null -> forDt(sub)
            subType!=null -> DataType(BaseDataType.STRUCT_INSTANCE, null, subType)
            subTypeFromAntlr!=null -> DataType(BaseDataType.STRUCT_INSTANCE, null, null, subTypeFromAntlr)
            else -> throw IllegalArgumentException("cannot dereference this pointer type")
        }
    }

    override fun toString(): String = when(base) {
        BaseDataType.ARRAY -> {
            when(sub) {
                BaseDataType.BOOL -> "bool[]"
                BaseDataType.FLOAT -> "float[]"
                BaseDataType.BYTE -> "byte[]"
                BaseDataType.WORD -> "word[]"
                BaseDataType.UBYTE -> "ubyte[]"
                BaseDataType.UWORD -> "uword[]"
                BaseDataType.LONG -> "long[]"
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
            if(sub!=null) "^^${sub.name.lowercase()}" else if(subType!=null) "^^${subType!!.scopedNameString}" else "^^${subTypeFromAntlr}"
        }
        BaseDataType.ARRAY_POINTER -> {
            if(sub!=null) "^^${sub.name.lowercase()}[] (split)" else if (subType!=null) "^^${subType!!.scopedNameString}[] (split)" else "^^${subTypeFromAntlr}[] (split)"
        }
        BaseDataType.STRUCT_INSTANCE -> {
            sub?.name?.lowercase() ?: if (subType!=null) subType!!.scopedNameString else "$subTypeFromAntlr"
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
            when {
                sub!=null -> "^^${sub.name.lowercase()}"
                subType!=null -> "^^${subType!!.scopedNameString}"
                subTypeFromAntlr!=null -> "^^${subTypeFromAntlr!!.joinToString(".")}"
                else -> "?????"
            }
        }
        BaseDataType.STRUCT_INSTANCE -> {
            when {
                sub!=null -> sub.name.lowercase()
                subType!=null -> subType!!.scopedNameString
                subTypeFromAntlr!=null -> subTypeFromAntlr!!.joinToString(".")
                else -> "?????"
            }
        }
        BaseDataType.ARRAY_POINTER -> {
            when {
                sub!=null -> "^^${sub.name.lowercase()}["
                subType!=null -> "^^${subType!!.scopedNameString}["
                subTypeFromAntlr!=null -> "^^${subTypeFromAntlr!!.joinToString(".")}["
                else -> "????? ["
            }
        }
        BaseDataType.ARRAY -> {
            when(sub) {
                BaseDataType.UBYTE -> "ubyte["
                BaseDataType.UWORD -> "@nosplit uword["
                BaseDataType.BOOL -> "bool["
                BaseDataType.BYTE -> "byte["
                BaseDataType.WORD -> "@nosplit word["
                BaseDataType.LONG -> "long["
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
            BaseDataType.UWORD -> targetType.base in arrayOf(BaseDataType.UWORD, BaseDataType.LONG, BaseDataType.FLOAT, BaseDataType.POINTER, BaseDataType.ARRAY_POINTER)
            BaseDataType.WORD -> targetType.base in arrayOf(BaseDataType.WORD, BaseDataType.LONG, BaseDataType.FLOAT)
            BaseDataType.LONG -> targetType.base in arrayOf(BaseDataType.LONG, BaseDataType.FLOAT)
            BaseDataType.FLOAT -> targetType.base in arrayOf(BaseDataType.FLOAT)
            BaseDataType.STR -> targetType.base in arrayOf(BaseDataType.STR, BaseDataType.UWORD) || (targetType.isPointer && targetType.sub==BaseDataType.UBYTE)
            BaseDataType.ARRAY, BaseDataType.ARRAY_SPLITW -> targetType.base in arrayOf(BaseDataType.ARRAY, BaseDataType.ARRAY_SPLITW) && targetType.sub == sub
            BaseDataType.POINTER -> {
                when {
                    targetType.base == BaseDataType.UWORD || targetType.base == BaseDataType.LONG -> true
                    targetType.isPointer -> this.isUnsignedWord || this == targetType
                    else -> false
                }
            }
            BaseDataType.STRUCT_INSTANCE -> false        // we cannot deal with actual struct instances yet in any shape or form (only getting fields from it)
            BaseDataType.ARRAY_POINTER -> false
            BaseDataType.UNDEFINED -> false
        }

    fun largerSizeThan(other: DataType): Boolean = base.largerSizeThan(other.base)
    fun equalsSize(other: DataType): Boolean = base.equalsSize(other.base)

    // note: for pointer types, size() doesn't return the size of the pointer itself but the size of the thing it points to
    fun size(memsizer: IMemSizer): Int = if(sub!=null) {
            memsizer.memorySize(sub)
        } else if(subType!=null) {
            subType!!.memsize(memsizer)
        } else {
            memsizer.memorySize(base)
        }

    val isBasic = sub==null && subType==null && subTypeFromAntlr==null
    val isUndefined = base == BaseDataType.UNDEFINED
    val isByte = base.isByte
    val isUnsignedByte = base == BaseDataType.UBYTE
    val isSignedByte = base == BaseDataType.BYTE
    val isByteOrBool = base.isByteOrBool
    val isWord = base.isWord
    val isUnsignedWord =  base == BaseDataType.UWORD
    val isSignedWord =  base == BaseDataType.WORD
    val isInteger = base.isInteger
    val isWordOrByteOrBool = base.isWordOrByteOrBool
    val isIntegerOrBool = base.isIntegerOrBool
    val isNumeric = base.isNumeric
    val isNumericOrBool = base.isNumericOrBool
    val isSigned = base.isSigned
    val isUnsigned = !base.isSigned
    val isArray = base.isArray
    val isPointer = base.isPointer
    val isPointerToByte = base.isPointer && sub?.isByteOrBool==true
    val isPointerToWord = base.isPointer && sub?.isWord==true
    val isStructInstance = base.isStructInstance
    val isPointerArray = base.isPointerArray
    val isBoolArray = base.isArray && !base.isPointerArray && sub == BaseDataType.BOOL
    val isByteArray = base.isArray && !base.isPointerArray && (sub == BaseDataType.UBYTE || sub == BaseDataType.BYTE)
    val isUnsignedByteArray = base.isArray && !base.isPointerArray && sub == BaseDataType.UBYTE
    val isSignedByteArray = base.isArray && !base.isPointerArray && sub == BaseDataType.BYTE
    val isWordArray = base.isArray && !base.isPointerArray && (sub == BaseDataType.UWORD || sub == BaseDataType.WORD)
    val isUnsignedWordArray = base.isArray && !base.isPointerArray && sub == BaseDataType.UWORD
    val isSignedWordArray = base.isArray && !base.isPointerArray && sub == BaseDataType.WORD
    val isLongArray = base.isArray && sub == BaseDataType.LONG
    val isFloatArray = base.isArray && !base.isPointerArray && sub == BaseDataType.FLOAT
    val isString = base == BaseDataType.STR
    val isBool = base == BaseDataType.BOOL
    val isFloat = base == BaseDataType.FLOAT
    val isLong = base == BaseDataType.LONG
    val isStringly = base == BaseDataType.STR || base == BaseDataType.UWORD || (base == BaseDataType.ARRAY && (sub == BaseDataType.UBYTE || sub == BaseDataType.BYTE))
    val isSplitWordArray = base.isSplitWordArray
    val isSplitUnsignedWordArray = base.isSplitWordArray && !base.isPointerArray && sub == BaseDataType.UWORD
    val isSplitSignedWordArray = base.isSplitWordArray && !base.isPointerArray && sub == BaseDataType.WORD
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
    R8, R9, R10, R11, R12, R13, R14, R15,
    // combined virtual registers to store 32 bits longs:
    R0R1_32, R2R3_32, R4R5_32, R6R7_32, R8R9_32, R10R11_32, R12R13_32, R14R15_32;

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

    /**
     * Returns the starting virtual register name for the current 32-bit combined virtual register
     * @return The starting register name as a string, WITHOUT THE cx16 block scope prefix!
     */
    fun startregname() = when(this) {
        R0R1_32 -> "r0"
        R2R3_32 -> "r2"
        R4R5_32 -> "r4"
        R6R7_32 -> "r6"
        R8R9_32 -> "r8"
        R10R11_32 -> "r10"
        R12R13_32 -> "r12"
        R14R15_32 -> "r14"
        else -> throw IllegalArgumentException("must be a combined virtual register $this")
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
            BaseDataType.UWORD, BaseDataType.POINTER, null -> ""
            else -> throw IllegalArgumentException("invalid register param type for cx16 virtual reg")
        }
        return listOf("cx16", name.lowercase()+suffix)
    }

    fun isWord() = this==AX || this == AY || this==XY || this in Cx16VirtualRegisters
    fun isLong() = this in combinedLongRegisters

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

val combinedLongRegisters = arrayOf(
    RegisterOrPair.R0R1_32,
    RegisterOrPair.R2R3_32,
    RegisterOrPair.R4R5_32,
    RegisterOrPair.R6R7_32,
    RegisterOrPair.R8R9_32,
    RegisterOrPair.R10R11_32,
    RegisterOrPair.R12R13_32,
    RegisterOrPair.R14R15_32
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
    NOSPLIT
}