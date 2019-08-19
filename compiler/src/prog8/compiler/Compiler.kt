package prog8.compiler

import prog8.ast.base.ArrayDatatypes
import prog8.ast.base.DataType
import prog8.ast.base.StringDatatypes
import prog8.ast.expressions.AddressOf
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import java.util.*
import kotlin.math.abs

enum class OutputType {
    RAW,
    PRG
}

enum class LauncherType {
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

data class IntegerOrAddressOf(val integer: Int?, val addressOf: AddressOf?)

data class CompilationOptions(val output: OutputType,
                              val launcher: LauncherType,
                              val zeropage: ZeropageType,
                              val zpReserved: List<IntRange>,
                              val floats: Boolean)


class CompilerException(message: String?) : Exception(message)

fun Number.toHex(): String {
    //  0..15 -> "0".."15"
    //  16..255 -> "$10".."$ff"
    //  256..65536 -> "$0100".."$ffff"
    // negative values are prefixed with '-'.
    val integer = this.toInt()
    if(integer<0)
        return '-' + abs(integer).toHex()
    return when (integer) {
        in 0 until 16 -> integer.toString()
        in 0 until 0x100 -> "$"+integer.toString(16).padStart(2,'0')
        in 0 until 0x10000 -> "$"+integer.toString(16).padStart(4,'0')
        else -> throw CompilerException("number too large for 16 bits $this")
    }
}

fun loadAsmIncludeFile(filename: String, source: Path): String {
    return if (filename.startsWith("library:")) {
        val resource = tryGetEmbeddedResource(filename.substring(8))
                ?: throw IllegalArgumentException("library file '$filename' not found")
        resource.bufferedReader().use { it.readText() }
    } else {
        // first try in the isSameAs folder as where the containing file was imported from
        val sib = source.resolveSibling(filename)
        if (sib.toFile().isFile)
            sib.toFile().readText()
        else
            File(filename).readText()
    }
}

internal fun tryGetEmbeddedResource(name: String): InputStream? {
    return object{}.javaClass.getResourceAsStream("/prog8lib/$name")
}

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
