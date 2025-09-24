package prog8.ast.expressions

import prog8.code.core.BaseDataType
import prog8.code.core.DataType
import java.util.*


object InferredTypes {
    class InferredType private constructor(val isUnknown: Boolean, val isVoid: Boolean, private val datatype: DataType?) {
        init {
            require(!(datatype!=null && (isUnknown || isVoid))) { "invalid combination of args" }
        }

        val isKnown get() = datatype!=null && !datatype.isUndefined
        fun getOr(default: DataType) = if(isUnknown || isVoid) default else datatype!!
        fun getOrUndef() = if(isUnknown || isVoid) DataType.UNDEFINED else datatype!!
        fun getOrElse(transform: (InferredType) -> DataType): DataType =
            if(isUnknown || isVoid) transform(this) else datatype!!
        infix fun istype(type: DataType): Boolean = if(isUnknown || isVoid) false else this.datatype==type     // strict equality if known
        infix fun issimpletype(type: BaseDataType): Boolean = if (isUnknown || isVoid)
                false
            else if(type==BaseDataType.STR && this.datatype?.base==BaseDataType.STR)
                true
            else (this.datatype?.base == type && this.datatype.isBasic)     // strict equality if known

        companion object {
            fun unknown() = InferredType(isUnknown = true, isVoid = false, datatype = null)
            fun void() = InferredType(isUnknown = false, isVoid = true, datatype = null)
            fun known(type: DataType) = InferredType(isUnknown = false, isVoid = false, datatype = type)
            fun known(basicdt: BaseDataType) = InferredType(isUnknown = false, isVoid = false, datatype = DataType.forDt(basicdt))
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
        infix fun isAssignableTo(targetDt: DataType): Boolean =
                isKnown && (datatype!! isAssignableTo targetDt)
        infix fun isNotAssignableTo(targetDt: InferredType): Boolean = !this.isAssignableTo(targetDt)
        infix fun isNotAssignableTo(targetDt: DataType): Boolean = !this.isAssignableTo(targetDt)

        val isBool = datatype?.isBool==true
        val isBytes = datatype?.isByte==true
        val isWords = datatype?.isWord==true
        val isLong = datatype?.isLong==true
        val isPointer = datatype?.isPointer==true
        val isStructInstance = datatype?.isStructInstance==true
        val isUnsignedWord = datatype?.isUnsignedWord==true
        val isInteger = datatype?.isInteger==true
        val isNumeric = datatype?.isNumeric==true
        val isNumericOrBool = datatype?.isNumericOrBool==true
        val isArray = datatype?.isArray==true
        val isFloatArray = datatype?.isFloatArray==true
        val isByteArray = datatype?.isByteArray==true
        val isString = datatype?.isString==true
        val isStringLy = datatype?.isStringly==true
        val isIterable = datatype?.isIterable==true
    }

    private val unknownInstance = InferredType.unknown()
    private val voidInstance = InferredType.void()

    private val instancesBaseDt = mapOf(
        BaseDataType.BOOL to InferredType.known(BaseDataType.BOOL),
        BaseDataType.UBYTE to InferredType.known(BaseDataType.UBYTE),
        BaseDataType.BYTE to InferredType.known(BaseDataType.BYTE),
        BaseDataType.UWORD to InferredType.known(BaseDataType.UWORD),
        BaseDataType.WORD to InferredType.known(BaseDataType.WORD),
        BaseDataType.LONG to InferredType.known(BaseDataType.LONG),
        BaseDataType.FLOAT to InferredType.known(BaseDataType.FLOAT),
        BaseDataType.STR to InferredType.known(BaseDataType.STR),
        BaseDataType.UNDEFINED to InferredType.known(BaseDataType.UNDEFINED)
    )

    private val instancesDt = mapOf(
        DataType.UBYTE to instancesBaseDt.getValue(BaseDataType.UBYTE),
        DataType.BYTE to instancesBaseDt.getValue(BaseDataType.BYTE),
        DataType.UWORD to instancesBaseDt.getValue(BaseDataType.UWORD),
        DataType.WORD to instancesBaseDt.getValue(BaseDataType.WORD),
        DataType.LONG to instancesBaseDt.getValue(BaseDataType.LONG),
        DataType.FLOAT to instancesBaseDt.getValue(BaseDataType.FLOAT),
        DataType.BOOL to instancesBaseDt.getValue(BaseDataType.BOOL),
        DataType.STR to instancesBaseDt.getValue(BaseDataType.STR),
        DataType.UNDEFINED to instancesBaseDt.getValue(BaseDataType.UNDEFINED)
    )

    fun void() = voidInstance
    fun unknown() = unknownInstance
    fun knownFor(baseDt: BaseDataType): InferredType = instancesBaseDt.getValue(baseDt)
    fun knownFor(type: DataType): InferredType {
        val instance = instancesDt[type]
        if(instance!=null)
            return instance
        else
            return when {
                type.isPointerArray -> InferredType.known(type)
                type.isSplitWordArray -> {
                    when (type.sub) {
                        BaseDataType.UWORD -> InferredType.known(DataType.arrayFor(BaseDataType.UWORD))
                        BaseDataType.WORD -> InferredType.known(DataType.arrayFor(BaseDataType.WORD))
                        BaseDataType.STR -> InferredType.known(DataType.arrayFor(BaseDataType.STR))
                        else -> throw IllegalArgumentException("invalid sub type")
                    }
                }

                type.isArray -> InferredType.known(type)
                type.isPointer -> InferredType.known(type)
                type.isStructInstance -> InferredType.known(type)
                else -> throw IllegalArgumentException("invalid type $type")
            }
    }
}
