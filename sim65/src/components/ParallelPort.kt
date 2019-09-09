package sim65.components

import sim65.Petscii

/**
 * A parallel output device (basically, prints bytes as characters to the screen)
 * First address = data byte (8 parallel bits)
 * Second address = control byte (bit 0 high = write byte)
 */
class ParallelPort(startAddress: Address, endAddress: Address) : MemMappedComponent(startAddress, endAddress) {
    var dataByte: UByte = 0

    init {
        require(endAddress - startAddress + 1 == 2) { "parallel needs exactly 2 memory bytes (data + control)" }
    }

    override fun clock() {}
    override fun reset() {}

    override operator fun get(address: Address): UByte {
        return if (address == startAddress)
            dataByte
        else
            0
    }

    override operator fun set(address: Address, data: UByte) {
        if (address == startAddress)
            dataByte = data
        else if (address == endAddress) {
            if ((data.toInt() and 1) == 1) {
                val char = Petscii.decodeScreencode(listOf(dataByte), false).first()
                println("PARALLEL WRITE: '$char'")
            }
        }
    }
}
