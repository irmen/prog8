package prog8.code.core

import java.util.*


/**
 * Base data types supported by the Prog8 compiler.
 * These represent the fundamental types that can be used in Prog8 programs.
 */
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


// ============================================================================
// BaseDataType Extension Properties
// ============================================================================

val BaseDataType.isByte get() = this in arrayOf(BaseDataType.UBYTE, BaseDataType.BYTE)
val BaseDataType.isUnsignedByte get() = this == BaseDataType.UBYTE
val BaseDataType.isSignedByte get() = this == BaseDataType.BYTE
val BaseDataType.isBool get() = this == BaseDataType.BOOL
val BaseDataType.isByteOrBool get() = this in arrayOf(BaseDataType.UBYTE, BaseDataType.BYTE, BaseDataType.BOOL)
val BaseDataType.isWord get() = this in arrayOf(BaseDataType.UWORD, BaseDataType.WORD)
val BaseDataType.isUnsignedWord get() = this == BaseDataType.UWORD
val BaseDataType.isSignedWord get() = this == BaseDataType.WORD
val BaseDataType.isLong get() = this == BaseDataType.LONG
val BaseDataType.isFloat get() = this == BaseDataType.FLOAT
val BaseDataType.isInteger get() = this in setOf(BaseDataType.UBYTE, BaseDataType.BYTE, BaseDataType.UWORD, BaseDataType.WORD, BaseDataType.LONG)
val BaseDataType.isIntegerOrBool get() = this in setOf(BaseDataType.UBYTE, BaseDataType.BYTE, BaseDataType.UWORD, BaseDataType.WORD, BaseDataType.LONG, BaseDataType.BOOL)
val BaseDataType.isWordOrByteOrBool get() = this in setOf(BaseDataType.UBYTE, BaseDataType.BYTE, BaseDataType.UWORD, BaseDataType.WORD, BaseDataType.BOOL)
val BaseDataType.isNumeric get() = this == BaseDataType.FLOAT || this.isInteger
val BaseDataType.isNumericOrBool get() = this == BaseDataType.BOOL || this.isNumeric
val BaseDataType.isSigned get() = this in setOf(BaseDataType.BYTE, BaseDataType.WORD, BaseDataType.LONG, BaseDataType.FLOAT)
val BaseDataType.isArray get() = this == BaseDataType.ARRAY || this == BaseDataType.ARRAY_SPLITW || this == BaseDataType.ARRAY_POINTER
val BaseDataType.isPointer get() = this == BaseDataType.POINTER
val BaseDataType.isStructInstance get() = this == BaseDataType.STRUCT_INSTANCE
val BaseDataType.isPointerArray get() = this == BaseDataType.ARRAY_POINTER
val BaseDataType.isSplitWordArray get() = this == BaseDataType.ARRAY_SPLITW || this == BaseDataType.ARRAY_POINTER       // pointer arrays are also always stored as split uwords
val BaseDataType.isIterable get() =  this in setOf(BaseDataType.STR, BaseDataType.ARRAY, BaseDataType.ARRAY_SPLITW, BaseDataType.ARRAY_POINTER)
val BaseDataType.isPassByRef get() = this.isIterable && !this.isPointer
val BaseDataType.isPassByValue get() = !this.isIterable || this.isPointer


/**
 * Interface for types that can be used as subtypes in DataType.
 * Primarily used for struct types.
 */
interface ISubType {
    val scopedNameString: String
    fun memsize(sizer: IMemSizer): Int
    fun sameas(other: ISubType): Boolean
    fun getFieldType(name: String): DataType?
}


/**
 * Represents a complete data type in Prog8, including base type and optional subtype.
 * 
 * DataType is immutable after construction. Use the companion object factory methods
 * to create instances.
 * 
 * @property base The base data type (e.g., UBYTE, WORD, ARRAY, POINTER)
 * @property sub The subtype for arrays and strings (e.g., UBYTE for byte arrays)
 * @property subType The structured subtype for pointers and struct instances
 * @property subTypeFromAntlr Deferred subtype resolution from parser
 */
class DataType private constructor(
    val base: BaseDataType,
    val sub: BaseDataType?,
    var subType: ISubType?,
    var subTypeFromAntlr: List<String>? = null
) {

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

    // ============================================================================
    // Companion Object - Factory Methods
    // ============================================================================
    
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


    // ============================================================================
    // DataType Methods
    // ============================================================================

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
        require(isPointer || isUnsignedWord) { "cannot dereference non-pointer type ${this}"}
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

    /**
     * Check if this type is assignable to the given target type (perhaps via a typecast)
     * without loss of precision.
     */
    infix fun isAssignableTo(targetType: DataType) =
        when(base) {
            BaseDataType.BOOL -> targetType.base == BaseDataType.BOOL
            BaseDataType.UBYTE -> targetType.base in setOf(BaseDataType.UBYTE, BaseDataType.WORD, BaseDataType.UWORD, BaseDataType.LONG, BaseDataType.FLOAT)
            BaseDataType.BYTE -> targetType.base in setOf(BaseDataType.BYTE, BaseDataType.WORD, BaseDataType.LONG, BaseDataType.FLOAT)
            BaseDataType.UWORD -> targetType.base in setOf(BaseDataType.UWORD, BaseDataType.LONG, BaseDataType.FLOAT, BaseDataType.POINTER, BaseDataType.ARRAY_POINTER)
            BaseDataType.WORD -> targetType.base in setOf(BaseDataType.WORD, BaseDataType.LONG, BaseDataType.FLOAT)
            BaseDataType.LONG -> targetType.base in setOf(BaseDataType.LONG, BaseDataType.FLOAT)
            BaseDataType.FLOAT -> targetType.base in arrayOf(BaseDataType.FLOAT)
            BaseDataType.STR -> targetType.base in setOf(BaseDataType.STR, BaseDataType.UWORD) || (targetType.isPointer && targetType.sub==BaseDataType.UBYTE)
            BaseDataType.ARRAY, BaseDataType.ARRAY_SPLITW -> targetType.base in setOf(BaseDataType.ARRAY, BaseDataType.ARRAY_SPLITW) && targetType.sub == sub
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

    /**
     * Returns the memory size in bytes.
     * Note: for pointer types, size() doesn't return the size of the pointer itself
     * but the size of the thing it points to.
     */
    fun size(memsizer: IMemSizer): Int = if(sub!=null) {
            memsizer.memorySize(sub)
        } else if(subType!=null) {
            subType!!.memsize(memsizer)
        } else {
            memsizer.memorySize(base)
        }

    // ============================================================================
    // DataType Properties
    // ============================================================================

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
    val isSignedInteger = isSigned && isInteger
    val isUnsignedInteger = isUnsigned && isInteger
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
