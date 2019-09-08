package sim65.components

class Rom(startAddress: Address, endAddress: Address, data: Array<UByte>? = null) : MemoryComponent(startAddress, endAddress) {
    private val memory =
            if (data == null)
                ShortArray(endAddress - startAddress - 1)
            else
                ShortArray(data.size) { index -> data[index] }

    init {
        if (data != null)
            require(endAddress - startAddress + 1 == data.size) { "rom address range doesn't match size of data bytes" }
    }

    override operator fun get(address: Address): UByte = memory[address - startAddress]
    override operator fun set(address: Address, data: UByte) {}
    override fun cloneContents(): Array<UByte> = memory.toTypedArray()
    override fun clock() {}
    override fun reset() {}
}
