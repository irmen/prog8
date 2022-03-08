package prog8.sim

class Memory {
    val mem = ByteArray(65536)

    operator fun get(addresss: UInt): UByte = getSByte(addresss).toUByte()

    operator fun set(address: UInt, value: UByte) = setSByte(address, value.toByte())

    fun getSByte(address: UInt): Byte = mem[address.toInt()]

    fun setSByte(address: UInt, value: Byte) {
        mem[address.toInt()] = value
    }

    fun getSWord(address: UInt): Int {
        val word = getWord(address).toInt()
        return if(word>=32768)
            -(65536-word)
        else
            word
    }

    fun setSWord(address: UInt, value: Int) = setWord(address, value.toUInt())

    fun getWord(address: UInt): UInt {
        val lsb = mem[address.toInt()].toUByte()
        val msb = mem[address.toInt()+1].toUByte()
        return lsb + msb*256u
    }

    fun setWord(address: UInt, value: UInt) {
        val lsb = value.toByte()
        val msb = (value shr 8).toByte()
        mem[address.toInt()] = lsb
        mem[address.toInt()+1] = msb
    }

    fun clear() {
        for(i in 0..65535)
            mem[i]=0
    }

    fun setString(address: UInt, str: String, zeroTerminate: Boolean=true) {
        var addr = address.toInt()
        for (it in str.toByteArray(Charsets.ISO_8859_1)) {
            mem[addr] = it
            addr++
        }
        if(zeroTerminate)
            mem[addr] = 0
    }

    fun getString(address: UInt): String {
        var addr = address.toInt()
        while(mem[addr] != 0.toByte()) addr++
        return String(mem, address.toInt(), addr-address.toInt(), Charsets.ISO_8859_1)
    }

    fun memset(address: UInt, length: UInt, value: UByte) {
        var addr=address.toInt()
        val byteval = value.toByte()
        repeat(length.toInt()) {
            mem[addr] = byteval
            addr++
        }
    }
}