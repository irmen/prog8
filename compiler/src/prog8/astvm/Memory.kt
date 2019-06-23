package prog8.astvm

import prog8.compiler.target.c64.Mflpt5
import prog8.compiler.target.c64.Petscii
import kotlin.math.abs

class Memory {
    private val mem = ShortArray(65536)         // shorts because byte is signed and we store values 0..255

    fun getUByte(address: Int): Short {
        return mem[address]
    }

    fun getSByte(address: Int): Short {
        val ubyte = getUByte(address)
        if(ubyte <= 127)
            return ubyte
        return (-((ubyte.toInt() xor 255)+1)).toShort()   // 2's complement
    }

    fun setUByte(address: Int, value: Short) {
        if(value !in 0..255)
            throw VmExecutionException("ubyte value out of range")
        mem[address] = value
    }

    fun setSByte(address: Int, value: Short) {
        if(value !in -128..127) throw VmExecutionException("byte value out of range")
        if(value>=0)
            mem[address] = value
        else
            mem[address] = ((abs(value.toInt()) xor 255)+1).toShort()        // 2's complement
    }

    fun getUWord(address: Int): Int {
        return mem[address] + 256*mem[address+1]
    }

    fun getSWord(address: Int): Int {
        val uword = getUWord(address)
        if(uword <= 32767)
            return uword
        return -((uword xor 65535)+1)   // 2's complement
    }

    fun setUWord(address: Int, value: Int) {
        if(value !in 0..65535)
            throw VmExecutionException("uword value out of range")
        mem[address] = value.and(255).toShort()
        mem[address+1] = (value / 256).toShort()
    }

    fun setSWord(address: Int, value: Int) {
        if(value !in -32768..32767) throw VmExecutionException("word value out of range")
        if(value>=0)
            setUWord(address, value)
        else
            setUWord(address, (abs(value) xor 65535)+1)        // 2's complement
    }

    fun setFloat(address: Int, value: Double) {
        val mflpt5 = Mflpt5.fromNumber(value)
        mem[address] = mflpt5.b0
        mem[address+1] = mflpt5.b1
        mem[address+2] = mflpt5.b2
        mem[address+3] = mflpt5.b3
        mem[address+4] = mflpt5.b4
    }

    fun getFloat(address: Int): Double {
        return Mflpt5(mem[address], mem[address + 1], mem[address + 2], mem[address + 3], mem[address + 4]).toDouble()
    }

    fun setString(address: Int, str: String) {
        // lowercase PETSCII
        val petscii = Petscii.encodePetscii(str, true)
        var addr = address
        for (c in petscii) mem[addr++] = c
        mem[addr] = 0
    }

    fun getString(strAddress: Int): String {
        // lowercase PETSCII
        val petscii = mutableListOf<Short>()
        var addr = strAddress
        while(true) {
            val byte = mem[addr++]
            if(byte==0.toShort()) break
            petscii.add(byte)
        }
        return Petscii.decodePetscii(petscii, true)
    }

    fun clear() {
        for(i in 0..65535) mem[i]=0
    }

    fun copy(from: Int, to: Int, numbytes: Int) {
        for(i in 0 until numbytes)
            mem[to+i] = mem[from+i]
    }
}
