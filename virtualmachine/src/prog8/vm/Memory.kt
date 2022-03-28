package prog8.vm

/**
 * 64 Kb of random access memory.
 */
class Memory {
    private val mem = Array<UByte>(64 * 1024) { 0u }

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
        return (256u*mem[address+1] + mem[address]).toUShort()
    }

    fun getSW(address: Int): Short {
        return (mem[address+1].toInt()*256 + mem[address].toInt()).toShort()
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

    // for now, no LONG 32-bits and no FLOAT support.
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