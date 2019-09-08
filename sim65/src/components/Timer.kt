package sim65.components

class Timer(startAddress: Address, endAddress: Address): MemMappedComponent(startAddress, endAddress) {
    private var cycle: Long = 0

    init {
        require(endAddress - startAddress + 1 == 4) { "timer needs exactly 4 memory bytes" }
    }

    override fun clock() {
        cycle++
        if (cycle > 0xffffffff)
            cycle = 0
    }

    override fun reset() {
        cycle = 0
    }

    override operator fun get(address: Address): UByte {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override operator fun set(address: Address, data: UByte) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun cloneMem(): Array<UByte> = TODO("clonemem timer")
}
