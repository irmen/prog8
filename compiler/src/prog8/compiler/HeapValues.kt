package prog8.compiler

import prog8.ast.base.ArrayDatatypes
import prog8.ast.base.DataType
import prog8.ast.base.StringDatatypes
import java.util.*

class HeapValues {
    data class HeapValue(val type: DataType, val str: String?, val array: Array<IntegerOrAddressOf>?, val doubleArray: DoubleArray?) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as HeapValue
            return type==other.type && str==other.str && Arrays.equals(array, other.array) && Arrays.equals(doubleArray, other.doubleArray)
        }

        override fun hashCode(): Int = Objects.hash(str, array, doubleArray)
    }

    private val heap = mutableMapOf<Int, HeapValue>()
    private var heapId = 1

    fun size(): Int = heap.size

    fun addString(type: DataType, str: String): Int {
        if (str.length > 255)
            throw IllegalArgumentException("string length must be 0-255")

        // strings are 'interned' and shared if they're the isSameAs
        val value = HeapValue(type, str, null, null)

        val existing = heap.filter { it.value==value }.map { it.key }.firstOrNull()
        if(existing!=null)
            return existing
        val newId = heapId++
        heap[newId] = value
        return newId
    }

    fun addIntegerArray(type: DataType, array: Array<IntegerOrAddressOf>): Int {
        // arrays are never shared, don't check for existing
        if(type !in ArrayDatatypes)
            throw CompilerException("wrong array type")
        val newId = heapId++
        heap[newId] = HeapValue(type, null, array, null)
        return newId
    }

    fun addDoublesArray(darray: DoubleArray): Int {
        // arrays are never shared, don't check for existing
        val newId = heapId++
        heap[newId] = HeapValue(DataType.ARRAY_F, null, null, darray)
        return newId
    }

    fun updateString(heapId: Int, str: String) {
        val oldVal = heap[heapId] ?: throw IllegalArgumentException("heapId not found in heap")
        if(oldVal.type in StringDatatypes) {
            if (oldVal.str!!.length != str.length)
                throw IllegalArgumentException("heap string length mismatch")
            heap[heapId] = oldVal.copy(str = str)
        }
        else throw IllegalArgumentException("heap data type mismatch")
    }

    fun get(heapId: Int): HeapValue {
        return heap[heapId] ?:
        throw IllegalArgumentException("heapId $heapId not found in heap")
    }
}
