package prog8.vm

import prog8.intermediate.RegisterNum

/**
 * 65536 virtual integer registers of 16 bits wide.
 * 65536 virtual float registers of 32 bits wide.
 * A,X and Y "physical" 6502 registers.
 */
class Registers {
    private val registers = Array(99999) { 0 }
    private val floatRegisters = Array(99999) { 0.0 }
    var cpuA: UByte = 0u
    var cpuX: UByte = 0u
    var cpuY: UByte = 0u

    fun reset() {
        registers.fill(0)
        floatRegisters.fill(0.0)
        cpuA = 0u
        cpuX = 0u
        cpuY = 0u
    }

    fun setUB(reg: Int, value: UByte) { registers[reg] = value.toInt() }
    fun setUB(reg: RegisterNum, value: UByte) { registers[reg.value] = value.toInt() }

    fun setSB(reg: Int, value: Byte) { registers[reg] = value.toInt() }
    fun setSB(reg: RegisterNum, value: Byte) { registers[reg.value] = value.toInt() }

    fun setUW(reg: Int, value: UShort) { registers[reg] = value.toInt() }
    fun setUW(reg: RegisterNum, value: UShort) { registers[reg.value] = value.toInt() }

    fun setSW(reg: Int, value: Short) { registers[reg] = value.toInt() }
    fun setSW(reg: RegisterNum, value: Short) { registers[reg.value] = value.toInt() }

    fun setSL(reg: Int, value: Int) { registers[reg] = value }
    fun setSL(reg: RegisterNum, value: Int) { registers[reg.value] = value }

    fun getUB(reg: Int) = registers[reg].toUByte()
    fun getUB(reg: RegisterNum) = registers[reg.value].toUByte()

    fun getSB(reg: Int) = registers[reg].toByte()
    fun getSB(reg: RegisterNum) = registers[reg.value].toByte()

    fun getUW(reg: Int) = registers[reg].toUShort()
    fun getUW(reg: RegisterNum) = registers[reg.value].toUShort()

    fun getSW(reg: Int) = registers[reg].toShort()
    fun getSW(reg: RegisterNum) = registers[reg.value].toShort()

    fun getSL(reg: Int) = registers[reg]
    fun getSL(reg: RegisterNum) = registers[reg.value]

    fun getFloat(reg: Int) = floatRegisters[reg]
    fun getFloat(reg: RegisterNum) = floatRegisters[reg.value]

    fun setFloat(reg: Int, value: Double) { floatRegisters[reg] = value }
    fun setFloat(reg: RegisterNum, value: Double) { floatRegisters[reg.value] = value }
}
