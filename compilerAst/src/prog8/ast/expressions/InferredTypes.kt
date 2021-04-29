package prog8.ast.expressions

import prog8.ast.base.*
import java.util.*


object InferredTypes {
    class InferredType private constructor(val isUnknown: Boolean, val isVoid: Boolean, private var datatype: DataType?) {
        init {
            require(!(datatype!=null && (isUnknown || isVoid))) { "invalid combination of args" }
        }

        val isKnown = datatype!=null
        fun typeOrElse(alternative: DataType) = if(isUnknown || isVoid) alternative else datatype!!
        infix fun istype(type: DataType): Boolean = if(isUnknown || isVoid) false else this.datatype==type

        companion object {
            fun unknown() = InferredType(isUnknown = true, isVoid = false, datatype = null)
            fun void() = InferredType(isUnknown = false, isVoid = true, datatype = null)
            fun known(type: DataType) = InferredType(isUnknown = false, isVoid = false, datatype = type)
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

        fun isBytes() = datatype in ByteDatatypes
        fun isWords() = datatype in WordDatatypes
        fun isInteger() = datatype in IntegerDatatypes
        fun isNumeric() = datatype in NumericDatatypes
        fun isArray() = datatype in ArrayDatatypes
        fun isString() = datatype in StringlyDatatypes
        fun isIterable() = datatype in IterableDatatypes
        fun isPassByReference() = datatype in PassByReferenceDatatypes
        fun isPassByValue() = datatype in PassByValueDatatypes
        fun isArrayElement() = datatype in ElementToArrayTypes
    }

    private val unknownInstance = InferredType.unknown()
    private val voidInstance = InferredType.void()
    private val knownInstances = mapOf(
            DataType.UBYTE to InferredType.known(DataType.UBYTE),
            DataType.BYTE to InferredType.known(DataType.BYTE),
            DataType.UWORD to InferredType.known(DataType.UWORD),
            DataType.WORD to InferredType.known(DataType.WORD),
            DataType.FLOAT to InferredType.known(DataType.FLOAT),
            DataType.STR to InferredType.known(DataType.STR),
            DataType.ARRAY_UB to InferredType.known(DataType.ARRAY_UB),
            DataType.ARRAY_B to InferredType.known(DataType.ARRAY_B),
            DataType.ARRAY_UW to InferredType.known(DataType.ARRAY_UW),
            DataType.ARRAY_W to InferredType.known(DataType.ARRAY_W),
            DataType.ARRAY_F to InferredType.known(DataType.ARRAY_F)
    )

    fun void() = voidInstance
    fun unknown() = unknownInstance
    fun knownFor(type: DataType) = knownInstances.getValue(type)
}
