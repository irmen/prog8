package prog8.ast.expressions

import prog8.ast.base.DataType
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
            DataType.STR_S to InferredType.known(DataType.STR_S),
            DataType.ARRAY_UB to InferredType.known(DataType.ARRAY_UB),
            DataType.ARRAY_B to InferredType.known(DataType.ARRAY_B),
            DataType.ARRAY_UW to InferredType.known(DataType.ARRAY_UW),
            DataType.ARRAY_W to InferredType.known(DataType.ARRAY_W),
            DataType.ARRAY_F to InferredType.known(DataType.ARRAY_F),
            DataType.STRUCT to InferredType.known(DataType.STRUCT)
    )

    fun void() = voidInstance
    fun unknown() = unknownInstance
    fun knownFor(type: DataType) = knownInstances.getValue(type)
}
