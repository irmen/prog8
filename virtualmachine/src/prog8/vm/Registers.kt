package prog8.vm

/**
 * 65536 virtual integer registers of 16 bits wide.
 * 65536 virtual float registers of 32 bits wide.
 * A,X and Y "physical" 6502 registers.
 */
class Registers {
    private val registers = Array<Int>(99999) { 0 }
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

    fun setUB(reg: Int, value: UByte) {
        registers[reg] = value.toInt()
    }

    fun setSB(reg: Int, value: Byte) {
        registers[reg] = value.toInt()
    }

    fun setUW(reg: Int, value: UShort) {
        registers[reg] = value.toInt()
    }

    fun setSW(reg: Int, value: Short) {
        registers[reg] = value.toInt()
    }

    fun setSL(reg: Int, value: Int) {
        registers[reg] = value
    }

    fun getUB(reg: Int) = registers[reg].toUByte()

    fun getSB(reg: Int) = registers[reg].toByte()

    fun getUW(reg: Int) = registers[reg].toUShort()

    fun getSW(reg: Int) = registers[reg].toShort()

    fun getSL(reg: Int) = registers[reg]

    fun getFloat(reg:Int) = floatRegisters[reg]

    fun setFloat(reg:Int, value: Double) {
        floatRegisters[reg] = value
    }
}
