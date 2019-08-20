package prog8.compiler

import prog8.ast.base.ArrayDatatypes
import prog8.ast.base.DataType
import java.util.*

/**
 * The 'heapvalues' is the collection of variables that are allocated globally.
 * Arrays and strings belong here.
 * They get assigned a heapId to be able to retrieve them later.
 */
class HeapValues {
    data class HeapValue(val type: DataType, val array: Array<IntegerOrAddressOf>?, val doubleArray: DoubleArray?) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as HeapValue
            return type==other.type && Arrays.equals(array, other.array) && Arrays.equals(doubleArray, other.doubleArray)
        }

        override fun hashCode(): Int = Objects.hash(type, array, doubleArray)
    }

    private val heap = mutableMapOf<Int, HeapValue>()
    private var heapId = 1

    fun size(): Int = heap.size

    fun addIntegerArray(type: DataType, array: Array<IntegerOrAddressOf>): Int {
        // arrays are never shared, don't check for existing
        if(type !in ArrayDatatypes)
            throw CompilerException("wrong array type $type")
        val newId = heapId++
        heap[newId] = HeapValue(type, array, null)
        return newId
    }

    fun addDoublesArray(darray: DoubleArray): Int {
        // arrays are never shared, don't check for existing
        val newId = heapId++
        heap[newId] = HeapValue(DataType.ARRAY_F, null, darray)
        return newId
    }

    fun get(heapId: Int): HeapValue {
        return heap[heapId] ?:
        throw IllegalArgumentException("heapId $heapId not found in heap")
    }

    fun remove(heapId: Int) {
        heap.remove(heapId)
    }
}
