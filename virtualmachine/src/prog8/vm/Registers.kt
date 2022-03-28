package prog8.vm

/**
 * 65536 virtual registers of 16 bits wide.
 */
class Registers {
    private val registers = Array<UShort>(65536) { 0u }

    fun reset() {
        registers.fill(0u)
    }

    fun setUB(reg: Int, value: UByte) {
        registers[reg] = registers[reg] and 0xff00u or value.toUShort()
    }

    fun setSB(reg: Int, value: Byte) {
        registers[reg] = registers[reg] and 0xff00u or (value.toUShort() and 0x00ffu)
    }

    fun setUW(reg: Int, value: UShort) {
        registers[reg] = value
    }

    fun setSW(reg: Int, value: Short) {
        registers[reg] = value.toUShort()
    }

    fun getUB(reg: Int) = registers[reg].toUByte()

    fun getSB(reg: Int) = registers[reg].toByte()

    fun getUW(reg: Int) = registers[reg]

    fun getSW(reg: Int) = registers[reg].toShort()
}
