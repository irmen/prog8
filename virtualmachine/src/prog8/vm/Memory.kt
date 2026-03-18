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

    fun getUB(address: Int): UByte {
        return mem[address].toUByte()
    }

    fun getSB(address: Int): Byte {
        return mem[address]
    }

    fun setUB(address: Int, value: UByte) {
        mem[address] = value.toByte()
    }

    fun setSB(address: Int, value: Byte) {
        mem[address] = value
    }

    fun getUW(address: Int): UShort {
        return ((mem[address].toInt() and 0xFF) or ((mem[address+1].toInt() and 0xFF) shl 8)).toUShort()
    }

    fun getSL(address: Int): Int {
        return (mem[address].toInt() and 0xFF) or
               ((mem[address+1].toInt() and 0xFF) shl 8) or
               ((mem[address+2].toInt() and 0xFF) shl 16) or
               ((mem[address+3].toInt() and 0xFF) shl 24)
    }

    fun getSW(address: Int): Short {
        return ((mem[address].toInt() and 0xFF) or ((mem[address+1].toInt() and 0xFF) shl 8)).toShort()
    }

    fun setUW(address: Int, value: UShort) {
        mem[address] = (value.toInt() and 0xFF).toByte()
        mem[address+1] = ((value.toInt() shr 8) and 0xFF).toByte()
    }

    fun setSW(address: Int, value: Short) {
        mem[address] = (value.toInt() and 0xFF).toByte()
        mem[address+1] = ((value.toInt() shr 8) and 0xFF).toByte()
    }

    fun setSL(address: Int, value: Int) {
        mem[address] = (value and 0xFF).toByte()
        mem[address+1] = ((value shr 8) and 0xFF).toByte()
        mem[address+2] = ((value shr 16) and 0xFF).toByte()
        mem[address+3] = ((value shr 24) and 0xFF).toByte()
    }

    fun setFloat(address: Int, value: Double) {
        var bits = value.toBits()
        mem[address] = (bits and 0xFF).toByte()
        bits = bits ushr 8
        mem[address+1] = (bits and 0xFF).toByte()
        bits = bits ushr 8
        mem[address+2] = (bits and 0xFF).toByte()
        bits = bits ushr 8
        mem[address+3] = (bits and 0xFF).toByte()
        bits = bits ushr 8
        mem[address+4] = (bits and 0xFF).toByte()
        bits = bits ushr 8
        mem[address+5] = (bits and 0xFF).toByte()
        bits = bits ushr 8
        mem[address+6] = (bits and 0xFF).toByte()
        bits = bits ushr 8
        mem[address+7] = (bits and 0xFF).toByte()
    }

    fun getFloat(address: Int): Double {
        val bits = (mem[address].toLong() and 0xFF) or
            ((mem[address+1].toLong() and 0xFF) shl 8) or
            ((mem[address+2].toLong() and 0xFF) shl 16) or
            ((mem[address+3].toLong() and 0xFF) shl 24) or
            ((mem[address+4].toLong() and 0xFF) shl 32) or
            ((mem[address+5].toLong() and 0xFF) shl 40) or
            ((mem[address+6].toLong() and 0xFF) shl 48) or
            ((mem[address+7].toLong() and 0xFF) shl 56)
        return Double.fromBits(bits)
    }

    fun setString(address: Int, string: String, zeroTerminated: Boolean) {
        val length = string.length
        // Batch copy using System.arraycopy for better performance
        val bytes = string.toByteArray()
        if (length > 0) {
            System.arraycopy(bytes, 0, mem, address, length)
        }
        if(zeroTerminated)
            mem[address + length] = 0
    }

    fun getString(address: Int): String {
        // Find the zero terminator first, then create string in one go
        var length = 0
        while (mem[address + length] != 0.toByte()) {
            length++
        }
        return String(mem, address, length)
    }
}