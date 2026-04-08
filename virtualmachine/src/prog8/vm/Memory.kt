package prog8.vm

import kotlin.random.Random

/**
 * 64 Kb of random access memory. Initialized to random values.
 */
class Memory {
    private val mem = ByteArray(64 * 1024) { Random.nextInt().toByte() }

    fun reset() {
        mem.fill(0)
    }

    fun getUB(address: UInt): UByte = mem[address.toInt()].toUByte()
    fun getSB(address: UInt): Byte = mem[address.toInt()]
    fun setUB(address: UInt, value: UByte) { mem[address.toInt()] = value.toByte() }
    fun setSB(address: UInt, value: Byte) { mem[address.toInt()] = value }

    fun getUW(address: UInt): UShort {
        val a = address.toInt()
        return ((mem[a].toInt() and 0xFF) or ((mem[a+1].toInt() and 0xFF) shl 8)).toUShort()
    }

    fun getSW(address: UInt): Short {
        val a = address.toInt()
        return ((mem[a].toInt() and 0xFF) or ((mem[a+1].toInt() and 0xFF) shl 8)).toShort()
    }

    fun getSL(address: UInt): Int {
        val a = address.toInt()
        return (mem[a].toInt() and 0xFF) or
               ((mem[a+1].toInt() and 0xFF) shl 8) or
               ((mem[a+2].toInt() and 0xFF) shl 16) or
               ((mem[a+3].toInt() and 0xFF) shl 24)
    }

    fun setUW(address: UInt, value: UShort) {
        val a = address.toInt()
        mem[a] = (value.toInt() and 0xFF).toByte()
        mem[a+1] = ((value.toInt() shr 8) and 0xFF).toByte()
    }

    fun setSW(address: UInt, value: Short) {
        val a = address.toInt()
        mem[a] = (value.toInt() and 0xFF).toByte()
        mem[a+1] = ((value.toInt() shr 8) and 0xFF).toByte()
    }

    fun setSL(address: UInt, value: Int) {
        val a = address.toInt()
        mem[a] = (value and 0xFF).toByte()
        mem[a+1] = ((value shr 8) and 0xFF).toByte()
        mem[a+2] = ((value shr 16) and 0xFF).toByte()
        mem[a+3] = ((value shr 24) and 0xFF).toByte()
    }

    fun setFloat(address: UInt, value: Double) {
        var bits = value.toBits()
        val a = address.toInt()
        mem[a] = (bits and 0xFF).toByte()
        bits = bits ushr 8
        mem[a+1] = (bits and 0xFF).toByte()
        bits = bits ushr 8
        mem[a+2] = (bits and 0xFF).toByte()
        bits = bits ushr 8
        mem[a+3] = (bits and 0xFF).toByte()
        bits = bits ushr 8
        mem[a+4] = (bits and 0xFF).toByte()
        bits = bits ushr 8
        mem[a+5] = (bits and 0xFF).toByte()
        bits = bits ushr 8
        mem[a+6] = (bits and 0xFF).toByte()
        bits = bits ushr 8
        mem[a+7] = (bits and 0xFF).toByte()
    }

    fun getFloat(address: UInt): Double {
        val a = address.toInt()
        val bits = (mem[a].toLong() and 0xFF) or
            ((mem[a+1].toLong() and 0xFF) shl 8) or
            ((mem[a+2].toLong() and 0xFF) shl 16) or
            ((mem[a+3].toLong() and 0xFF) shl 24) or
            ((mem[a+4].toLong() and 0xFF) shl 32) or
            ((mem[a+5].toLong() and 0xFF) shl 40) or
            ((mem[a+6].toLong() and 0xFF) shl 48) or
            ((mem[a+7].toLong() and 0xFF) shl 56)
        return Double.fromBits(bits)
    }

    fun setString(address: UInt, string: String, zeroTerminated: Boolean) {
        val a = address.toInt()
        val length = string.length
        if (length > 0)
            System.arraycopy(string.toByteArray(), 0, mem, a, length)
        if(zeroTerminated)
            mem[a + length] = 0
    }

    fun getString(address: UInt): String {
        val a = address.toInt()
        var length = 0
        while (mem[a + length] != 0.toByte()) length++
        return String(mem, a, length)
    }
}