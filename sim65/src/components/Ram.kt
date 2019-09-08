package sim65.components

import java.io.File

class Ram(startAddress: Address, endAddress: Address): MemoryComponent(startAddress, endAddress) {
    private val memory = ShortArray(endAddress-startAddress+1)

    override operator fun get(address: Address): UByte = memory[address-startAddress]

    override operator fun set(address: Address, data: UByte) {
        memory[address-startAddress] = data
    }

    override fun cloneContents(): Array<UByte> = memory.toTypedArray()

    override fun clock() { }

    override fun reset() {
        // contents of RAM doesn't change on a reset
    }

    fun fill(data: UByte) {
        memory.fill(data)
    }

    /**
     * load a c64-style prg program at the given address,
     * this file has the load address as the first two bytes.
     */
    fun loadPrg(filename: String) {
        val bytes = File(filename).readBytes()
        val address = (bytes[0].toInt() or (bytes[1].toInt() shl 8)) and 65535
        bytes.drop(2).forEachIndexed { index, byte ->
            memory[address+index] =
                    if(byte>=0)
                        byte.toShort()
                    else
                        (256+byte).toShort()
        }
    }

    /**
     * load a binary program at the given address
     */
    fun load(filename: String, address: Address) {
        val bytes = File(filename).readBytes()
        bytes.forEachIndexed { index, byte ->
            memory[address+index] =
                    if(byte>=0)
                        byte.toShort()
                    else
                        (256+byte).toShort()
        }
    }
}
