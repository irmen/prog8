package prog8.vm.astvm

import prog8.compiler.target.c64.Mflpt5
import prog8.compiler.target.c64.Petscii
import kotlin.math.abs

class Memory(private val readObserver: (address: Int, value: Short) -> Short,
             private val writeObserver: (address: Int, value: Short) -> Short)
{

    private val mem = ShortArray(65536)         // shorts because byte is signed and we store values 0..255
    private val observed = BooleanArray(65536)     // what addresses are observed


    fun observe(vararg address: Int) {
        address.forEach { observed[it]=true }
    }

    fun getUByte(address: Int): Short {
        return if(observed[address]) readObserver(address, mem[address])
        else mem[address]
    }

    fun getUByte_DMA(address: Int): Short {
        return mem[address]
    }

    fun getSByte(address: Int): Short {
        val ubyte = getUByte(address)
        return if(ubyte <= 127) ubyte
        else (-((ubyte.toInt() xor 255)+1)).toShort()   // 2's complement
    }

    fun setUByte(address: Int, value: Short) {
        if(value !in 0..255)
            throw VmExecutionException("ubyte value out of range $value")
        mem[address] =
                if(observed[address]) writeObserver(address, value)
                else value
    }

    fun setUByte_DMA(address: Int, value: Short) {
        if(value !in 0..255)
            throw VmExecutionException("ubyte value out of range $value")
        mem[address] = value
    }

    fun setSByte(address: Int, value: Short) {
        if(value !in -128..127) throw VmExecutionException("byte value out of range $value")
        val ubyte =
                if(value>=0) value
                else ((abs(value.toInt()) xor 255)+1).toShort()        // 2's complement
        setUByte(address, ubyte)
    }

    fun getUWord(address: Int): Int {
        return getUByte(address) + 256*getUByte(address+1)
    }

    fun getSWord(address: Int): Int {
        val uword = getUWord(address)
        if(uword <= 32767)
            return uword
        return -((uword xor 65535)+1)   // 2's complement
    }

    fun setUWord(address: Int, value: Int) {
        if(value !in 0..65535)
            throw VmExecutionException("uword value out of range $value")
        setUByte(address, value.and(255).toShort())
        setUByte(address+1, (value / 256).toShort())
    }

    fun setSWord(address: Int, value: Int) {
        if(value !in -32768..32767) throw VmExecutionException("word value out of range $value")
        if(value>=0)
            setUWord(address, value)
        else
            setUWord(address, (abs(value) xor 65535)+1)        // 2's complement
    }

    fun setFloat(address: Int, value: Double) {
        val mflpt5 = Mflpt5.fromNumber(value)
        setUByte(address, mflpt5.b0)
        setUByte(address+1, mflpt5.b1)
        setUByte(address+2, mflpt5.b2)
        setUByte(address+3, mflpt5.b3)
        setUByte(address+4, mflpt5.b4)
    }

    fun getFloat(address: Int): Double {
        return Mflpt5(getUByte(address), getUByte(address + 1), getUByte(address + 2),
                getUByte(address + 3), getUByte(address + 4)).toDouble()
    }

    fun setString(address: Int, str: String) {
        // lowercase PETSCII
        val petscii = Petscii.encodePetscii(str, true)
        var addr = address
        for (c in petscii) setUByte(addr++, c)
        setUByte(addr, 0)
    }

    fun getString(strAddress: Int): String {
        // lowercase PETSCII
        val petscii = mutableListOf<Short>()
        var addr = strAddress
        while(true) {
            val byte = getUByte(addr++)
            if(byte==0.toShort()) break
            petscii.add(byte)
        }
        return Petscii.decodePetscii(petscii, true)
    }

    fun clear() {
        for(i in 0..65535) setUByte(i, 0)
    }

    fun copy(from: Int, to: Int, numbytes: Int) {
        for(i in 0 until numbytes)
            setUByte(to+i, getUByte(from+i))
    }

    fun getScreencodeString(strAddress: Int): String? {
        // lowercase Screencodes
        val screencodes = mutableListOf<Short>()
        var addr = strAddress
        while(true) {
            val byte = getUByte(addr++)
            if(byte==0.toShort()) break
            screencodes.add(byte)
        }
        return Petscii.decodeScreencode(screencodes, true)
    }

    fun setScreencodeString(address: Int, str: String) {
        // lowercase screencodes
        val screencodes = Petscii.encodeScreencode(str, true)
        var addr = address
        for (c in screencodes) setUByte(addr++, c)
        setUByte(addr, 0)
    }
}
