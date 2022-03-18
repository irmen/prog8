package prog8.vm

/**
 * 65536 virtual registers of 16 bits wide.
 */
class Registers {
    private val registers = Array<UShort>(65536) { 0u }

    fun reset() {
        registers.fill(0u)
    }

    fun setB(reg: Int, value: UByte) {
        registers[reg] = registers[reg] and 0xff00u or value.toUShort()
    }

    fun setW(reg: Int, value: UShort) {
        registers[reg] = value
    }

    fun getB(reg: Int) = registers[reg].toUByte()

    fun getW(reg: Int) = registers[reg]
}