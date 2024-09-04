package prog8.ast.expressions

import prog8.code.core.*
import java.util.Objects


object InferredTypes {
    class InferredType private constructor(val isUnknown: Boolean, val isVoid: Boolean, private var datatype: DataTypeFull?) {
        init {
            require(!(datatype!=null && (isUnknown || isVoid))) { "invalid combination of args" }
        }

        val isKnown get() = datatype!=null && !datatype!!.isUndefined
        fun getOr(default: DataTypeFull) = if(isUnknown || isVoid) default else datatype!!
        fun getOrUndef() = if(isUnknown || isVoid) DataTypeFull.forDt(BaseDataType.UNDEFINED) else datatype!!
        fun getOrElse(transform: (InferredType) -> DataTypeFull): DataTypeFull =
            if(isUnknown || isVoid) transform(this) else datatype!!
        infix fun istype(type: DataTypeFull): Boolean = if(isUnknown || isVoid) false else this.datatype==type     // strict equality if known
        infix fun issimpletype(type: BaseDataType): Boolean = if(isUnknown || isVoid) false else (this.datatype?.dt==type && this.datatype?.sub==null)     // strict equality if known

        companion object {
            fun unknown() = InferredType(isUnknown = true, isVoid = false, datatype = null)
            fun void() = InferredType(isUnknown = false, isVoid = true, datatype = null)
            fun known(type: DataTypeFull) = InferredType(isUnknown = false, isVoid = false, datatype = type)
            fun known(basicdt: BaseDataType) = InferredType(isUnknown = false, isVoid = false, datatype = DataTypeFull.forDt(basicdt))
        }

        override fun equals(other: Any?): Boolean {
            if(other !is InferredType)
                return false
            return isVoid==other.isVoid && datatype==other.datatype
        }

        override fun toString(): String {
            return when {
                datatype!=null -> datatype.toString()
                isVoid -> "<void>"
                else -> "<unknown>"
            }
        }

        override fun hashCode(): Int = Objects.hash(isVoid, datatype)

        infix fun isAssignableTo(targetDt: InferredType): Boolean =
                isKnown && targetDt.isKnown && (datatype!! isAssignableTo targetDt.datatype!!)
        infix fun isAssignableTo(targetDt: DataTypeFull): Boolean =
                isKnown && (datatype!! isAssignableTo targetDt)
        infix fun isNotAssignableTo(targetDt: InferredType): Boolean = !this.isAssignableTo(targetDt)
        infix fun isNotAssignableTo(targetDt: DataTypeFull): Boolean = !this.isAssignableTo(targetDt)

        val isBool get() = datatype?.isBool==true
        val isBytes get() = datatype?.isByte==true
        val isWords get() = datatype?.isWord==true
        val isInteger get() = datatype?.isInteger==true
        val isNumeric get() = datatype?.isNumeric==true
        val isNumericOrBool get() = datatype?.isNumericOrBool==true
        val isArray get() = datatype?.isArray==true
        val isFloatArray get() = datatype?.isFloatArray==true
        val isByteArray get() = datatype?.isByteArray==true
        val isString get() = datatype?.isString==true
        val isStringLy get() = datatype?.isStringly==true
        val isIterable get() = datatype?.isIterable==true
    }

    private val unknownInstance = InferredType.unknown()
    private val voidInstance = InferredType.void()

    fun void() = voidInstance
    fun unknown() = unknownInstance
    fun knownFor(type: DataTypeFull): InferredType {
        return when {
            type.isUnsignedByte -> InferredType.known(BaseDataType.UBYTE)
            type.isSignedByte -> InferredType.known(BaseDataType.BYTE)
            type.isUnsignedWord -> InferredType.known(BaseDataType.UWORD)
            type.isSignedWord -> InferredType.known(BaseDataType.WORD)
            type.isBool -> InferredType.known(BaseDataType.BOOL)
            type.isFloat -> InferredType.known(BaseDataType.FLOAT)
            type.isString -> InferredType.known(BaseDataType.STR)
            type.isLong -> InferredType.known(BaseDataType.LONG)
            type.isSplitWordArray -> {
                when(type.sub?.dt) {
                    BaseDataType.UWORD -> InferredType.known(DataTypeFull.arrayFor(BaseDataType.UWORD, true))
                    BaseDataType.WORD -> InferredType.known(DataTypeFull.arrayFor(BaseDataType.WORD, true))
                    else -> throw IllegalArgumentException("invalid sub type")
                }
            }
            type.isArray -> {
                when(type.sub?.dt) {
                    BaseDataType.UBYTE -> InferredType.known(DataTypeFull.arrayFor(BaseDataType.UBYTE))
                    BaseDataType.UWORD -> InferredType.known(DataTypeFull.arrayFor(BaseDataType.UWORD))
                    BaseDataType.BYTE -> InferredType.known(DataTypeFull.arrayFor(BaseDataType.BYTE))
                    BaseDataType.WORD -> InferredType.known(DataTypeFull.arrayFor(BaseDataType.WORD))
                    BaseDataType.BOOL -> InferredType.known(DataTypeFull.arrayFor(BaseDataType.BOOL))
                    BaseDataType.FLOAT -> InferredType.known(DataTypeFull.arrayFor(BaseDataType.FLOAT))
                    else -> throw IllegalArgumentException("invalid sub type")
                }
            }
            else -> throw IllegalArgumentException("invalid type")
        }
    }
}
