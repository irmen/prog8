package prog8.vm

import kotlin.random.Random

/**
 * 64 Kb of random access memory. Initialized to random values.
 */
class Memory {
    private val mem = Array(64 * 1024) { Random.nextInt().toUByte() }

    fun reset() {
        mem.fill(0u)
    }

    fun getUB(address: Int): UByte {
        return mem[address]
    }

    fun getSB(address: Int): Byte {
        return mem[address].toByte()
    }

    fun setUB(address: Int, value: UByte) {
        mem[address] = value
    }

    fun setSB(address: Int, value: Byte) {
        mem[address] = value.toUByte()
    }

    fun getUW(address: Int): UShort {
        return (mem[address] + 256u*mem[address+1]).toUShort()
    }

    fun getSW(address: Int): Short {
        return (mem[address].toInt() + mem[address+1].toInt()*256).toShort()
    }

    fun setUW(address: Int, value: UShort) {
        mem[address+1]  = (value.toInt() ushr 8).toUByte()
        mem[address] = value.toUByte()
    }

    fun setSW(address: Int, value: Short) {
        val uv = value.toUShort()
        mem[address+1]  = (uv.toInt() ushr 8).toUByte()
        mem[address] = uv.toUByte()
    }

    fun setFloat(address: Int, value: Double) {
        var bits = value.toBits()
        mem[address] = bits.toUByte()
        bits = bits ushr 8
        mem[address+1] = bits.toUByte()
        bits = bits ushr 8
        mem[address+2] = bits.toUByte()
        bits = bits ushr 8
        mem[address+3] = bits.toUByte()
        bits = bits ushr 8
        mem[address+4] = bits.toUByte()
        bits = bits ushr 8
        mem[address+5] = bits.toUByte()
        bits = bits ushr 8
        mem[address+6] = bits.toUByte()
        bits = bits ushr 8
        mem[address+7] = bits.toUByte()
    }

    fun getFloat(address: Int): Double {
        val bits = mem[address].toLong() +
            (1L shl 8)*mem[address+1].toLong() +
            (1L shl 16)*mem[address+2].toLong() +
            (1L shl 24)*mem[address+3].toLong() +
            (1L shl 32)*mem[address+4].toLong() +
            (1L shl 40)*mem[address+5].toLong() +
            (1L shl 48)*mem[address+6].toLong() +
            (1L shl 56)*mem[address+7].toLong()
        return Double.fromBits(bits)
    }

// for now, no LONG 32-bits support
//    fun getL(address: Int): UInt {
//        return mem[address+3] + 256u*mem[address+2] + 65536u*mem[address+1] + 16777216u*mem[address]
//    }
//
//    fun setL(address: Int, value: UInt) {
//        val v = value.toInt()
//        mem[address] = (v ushr 24).toUByte()
//        mem[address+1] = (v ushr 16).toUByte()
//        mem[address+2] = (v ushr 8).toUByte()
//        mem[address+3] = v.toUByte()
//    }

    fun setString(address: Int, string: String, zeroTerminated: Boolean) {
        var addr=address
        for (c in string) {
            mem[addr] = c.code.toUByte()
            addr++
        }
        if(zeroTerminated)
            mem[addr] = 0u
    }

    fun getString(address: Int): String {
        val sb = StringBuilder()
        var addr = address
        while(true) {
            val char = mem[addr].toInt()
            if(char==0)
                break
            sb.append(Char(char))
            addr++
        }
        return sb.toString()
    }
}