package sim65.components

import sim65.C64Screencodes

/**
 * A parallel output device (basically, prints bytes as characters to the screen)
 * First address = data byte (8 parallel bits)
 * Second address = control byte (bit 0 high = write byte)
 */
class Parallel(startAddress: Address, endAddress: Address) : MemMappedComponent(startAddress, endAddress) {
    private var dataByte: UByte = 0

    init {
        require(endAddress - startAddress + 1 == 2) { "parallel needs exactly 2 memory bytes (data + control)" }
    }

    override fun clock() {}
    override fun reset() {}

    override fun read(address: Address): UByte {
        return if (address == startAddress)
            dataByte
        else
            0
    }

    override fun write(address: Address, data: UByte) {
        if (address == startAddress)
            dataByte = data
        else if (address == endAddress) {
            if ((data.toInt() and 1) == 1) {
                val char = C64Screencodes.decodeScreencode(listOf(dataByte), false).first()
                println("PARALLEL WRITE: '$char'")
            }
        }
    }

    override fun cloneMem(): Array<UByte> = listOf(dataByte, 0).toTypedArray()
}
