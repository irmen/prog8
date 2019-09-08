package sim65.components

class Rom(startAddress: Address, endAddress: Address, data: Array<UByte>): MemMappedComponent(startAddress, endAddress) {
    private val memory = ShortArray(data.size) { index -> data[index] }

    init {
        require(endAddress-startAddress+1 == data.size) { "rom address range doesn't match size of data bytes" }
    }

    override operator fun get(address: Address): UByte = memory[address-startAddress]
    override operator fun set(address: Address, data: UByte) { }
    override fun cloneMem(): Array<UByte> = memory.toTypedArray()
    override fun clock() { }
    override fun reset() { }
}
