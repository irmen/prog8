package sim65.components

import java.io.File

class Ram(startAddress: Address, endAddress: Address): MemMappedComponent(startAddress, endAddress) {
    private val memory = ShortArray(endAddress-startAddress+1)

    override fun read(address: Address): UByte = memory[address-startAddress]

    override fun write(address: Address, data: UByte) {
        memory[address-startAddress] = data
    }

    override fun cloneMem(): Array<UByte> = memory.toTypedArray()

    override fun clock() { }

    override fun reset() { memory.fill(0) }

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
